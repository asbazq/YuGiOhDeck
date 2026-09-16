package com.card.Yugioh.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


import org.apache.hc.client5.http.fluent.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.card.Yugioh.model.CardImage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImageService {
    // Local-server default: at most two concurrent downloads, with the shared rate limit below.
    @Value("${card.image.download-concurrency:2}")
    private int downloadConcurrency = 2;
    // 연결 수립과 응답 본문 읽기가 무한정 멈추지 않도록 각각 제한 시간을 둔다.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    // 일시적인 네트워크 오류나 5xx/429 응답에 대응하기 위한 파일별 최대 시도 횟수다.
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    // 다운로드 워커가 있더라도 HTTP 요청 시작 시점은 전체 기준 최소 250ms 간격으로 직렬화한다.
    // 즉, 이 애플리케이션 인스턴스가 새 요청을 시작하는 속도는 최대 약 4회/초다.
    private static final long REQUEST_INTERVAL_MS = 250L;
    // 서버의 Retry-After 값이 지나치게 커 작업이 장시간 정지하는 것을 막는 상한이다.
    private static final long MAX_RETRY_AFTER_MS = 60_000L;
    // 스케줄러 실행과 관리자 수동 실행이 겹쳐 동일 이미지를 중복 다운로드하는 것을 막는다.
    // compareAndSet으로 확인과 상태 변경을 하나의 원자적 연산으로 처리한다.
    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    // 모든 다운로드 워커가 공유하는 다음 요청 허용 시각. synchronized 메서드 안에서만 갱신한다.
    private long nextRequestAtMillis;
    private final CardPersistenceService cardPersistence;
    private final CardCatalogService catalog;

    @Value("${card.image.save-path}")
    private String savePathString;
    @Value("${card.image.small.save-path}")
    private String saveSmallPathString;

    private Path savePath;
    private Path saveSmallPath;

    @PostConstruct
    private void init() {
        this.savePath = Paths.get(savePathString);
        this.saveSmallPath = Paths.get(saveSmallPathString);
    }

    /** Manual fetch bypasses version caching; use this to repair missing local data. */
    public int fetchAndSaveCardImages(String apiUrl) throws IOException {
        if (!fetchInProgress.compareAndSet(false, true)) throw new ImageFetchAlreadyRunningException();
        try {
            IngestionResult result = ingestResponse(fetchJson(apiUrl));
            catalog.export(true);
            return result.processed();
        } finally {
            fetchInProgress.set(false);
        }
    }

    /** Weekly check: unchanged versions perform no card DB reads or image downloads. */
    public int fetchChangedCardImages(String apiUrl) throws IOException {
        if (!fetchInProgress.compareAndSet(false, true)) throw new ImageFetchAlreadyRunningException();
        try {
            Files.createDirectories(savePath);
            Path stateFile = savePath.resolve(".ingestion-state.json");
            JSONObject state = Files.exists(stateFile)
                ? new JSONObject(Files.readString(stateFile)) : new JSONObject();
            JSONArray versions = new JSONArray(fetchJson("https://db.ygoprodeck.com/api/v7/checkDBVer.php"));
            String version = versions.getJSONObject(0).get("database_version").toString();
            boolean sameVersion = version.equals(state.optString("version"))
                && apiUrl.equals(state.optString("url"));
            // A changed version can precede the upstream card JSON cache refresh (up to 48h).
            // Confirm once after that window, rather than permanently accepting a stale snapshot.
            long now = System.currentTimeMillis();
            boolean needsConfirmation = !state.optBoolean("confirmed")
                && now - state.optLong("observedAt", 0) >= TimeUnit.DAYS.toMillis(2);
            if (sameVersion && state.optBoolean("complete") && !needsConfirmation) {
                log.info("Card DB version unchanged: {}. Skipping ingestion.", version);
                catalog.export(false);
                return 0;
            }
            String response;
            if (sameVersion && !needsConfirmation && state.has("response")) {
                response = state.getString("response"); // retry incomplete work without another card API call
            } else {
                response = fetchJson(apiUrl);
            }
            JSONObject next = new JSONObject().put("version", version).put("url", apiUrl)
                .put("observedAt", sameVersion ? state.optLong("observedAt", now) : now)
                .put("confirmed", sameVersion && needsConfirmation)
                .put("response", response).put("complete", false);
            writeState(stateFile, next); // crash/failure never records a successful sync
            IngestionResult result = ingestResponse(response);
            catalog.export(true);
            next.put("complete", result.failed() == 0);
            writeState(stateFile, next);
            return result.processed();
        } finally {
            fetchInProgress.set(false);
        }
    }

    private String fetchJson(String url) throws IOException {
        Request request = Request.get(url);
        request.connectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(10));
        request.responseTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(30));
        return request.execute().returnContent().asString();
    }

    private void writeState(Path path, JSONObject state) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, state.toString());
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private IngestionResult ingestResponse(String response) throws IOException {
        JSONArray cardData = new JSONObject(response).getJSONArray("data");
        Files.createDirectories(savePath);
        Files.createDirectories(saveSmallPath);
        List<ImageDownloadTask> downloads = new ArrayList<>();
        int processed = 0;
        for (int i = 0; i < cardData.length(); i++) {
            try {
                List<CardImage> images = cardPersistence.ingest(cardData.getJSONObject(i).toString());
                processed++;
                for (CardImage image : images) {
                    queueMissingImage(downloads, image.getImageUrl(), savePath.resolve(image.getId() + ".jpg"));
                    queueMissingImage(downloads, image.getImageUrlSmall(), saveSmallPath.resolve(image.getId() + ".jpg"));
                }
            } catch (Exception e) {
                log.error("Card ingestion failed at index {}. Other cards will continue.", i, e);
            }
        }
        int failedImages = downloadImages(downloads);
        log.info("Card ingestion complete. saved={}, failed={}, failedImages={}", processed, cardData.length() - processed, failedImages);
        if (!cardData.isEmpty() && processed == 0) throw new IOException("No cards could be ingested; check per-card errors");
        return new IngestionResult(processed, cardData.length() - processed + failedImages);
    }

    private record IngestionResult(int processed, int failed) {}

    private void queueMissingImage(List<ImageDownloadTask> downloads, String url, Path output) {
        if (url != null && !url.isBlank() && Files.notExists(output)) {
            downloads.add(new ImageDownloadTask(url, output));
        }
    }

    private int downloadImages(List<ImageDownloadTask> tasks) {
        if (tasks.isEmpty()) {
            return 0;
        }

        // Clamp to a small worker pool on local servers.
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(downloadConcurrency, 2)));
        try {
            // Callable은 성공 시 저장 경로를 반환하고, 실패 시 예외를 Future에 보관한다.
            /*
             * 작업 데이터(ImageDownloadTask)를 실행 가능한 함수(Callable)로 바꾼다.
             * 이 map은 다운로드를 실행하지 않는다. 나중에 워커가 호출할 함수 객체만 만든다.
             * 실제 saveImageWithRetry 호출은 invokeAll 이후 워커 스레드에서 발생한다.
             */
            List<Callable<Path>> jobs = tasks.stream()
                .<Callable<Path>>map(task -> () -> {
                    saveImageWithRetry(task.imageUrl(), task.output());
                    return task.output();
                })
                .toList();

            /*
             * invokeAll은 작업을 워커에 병렬 배치하지만 모든 작업이 끝날 때까지 호출 스레드를 기다리게 한다.
             * 따라서 내부 다운로드는 멀티스레드 병렬 처리지만 fetchAndSaveCardImages 호출 자체는 동기다.
             */
            List<Future<Path>> futures = executor.invokeAll(jobs);
            // Future 순서는 입력 jobs 순서와 같으므로 같은 인덱스로 원래 URL을 찾을 수 있다.
            int successCount = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    // invokeAll이 완료를 기다렸으므로 get은 결과 또는 작업 예외를 즉시 꺼내는 역할을 한다.
                    futures.get(i).get();
                    successCount++;
                } catch (ExecutionException e) {
                    // 한 파일의 최종 실패가 다른 다운로드와 전체 배치를 중단시키지 않도록 개별 처리한다.
                    log.error("Image download failed after retries: {}", tasks.get(i).imageUrl(), e.getCause());
                }
            }
            log.info("Image downloads complete. success={}, failed={}", successCount, tasks.size() - successCount);
            return tasks.size() - successCount;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image downloads interrupted. Remaining downloads were cancelled.");
            return tasks.size();
        } finally {
            // 요청마다 만든 스레드 풀이 애플리케이션에 남지 않도록 반드시 종료한다.
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Image download executor did not terminate within 5 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 파일 한 개를 재시도 정책과 함께 다운로드한다. 이 메서드는 워커 스레드에서 실행된다.
     * IOException을 잡아 재시도하고, 마지막 시도도 실패하면 다시 던진다. 던져진 예외는
     * 워커 스레드를 넘어 Future 내부에 저장되고 호출 스레드의 Future.get에서
     * ExecutionException으로 관찰된다.
     */
    private void saveImageWithRetry(String imageUrl, Path output) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                saveImageFromUrl(imageUrl, output);
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
                    // 1차 실패 후 500ms, 2차 실패 후 1000ms 대기하는 지수 백오프다.
                    long backoffMs = 500L * (1L << (attempt - 1));
                    log.warn("Image download attempt {}/{} failed: {}. Retrying in {} ms",
                        attempt, MAX_DOWNLOAD_ATTEMPTS, imageUrl, backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Image download interrupted: " + imageUrl, interrupted);
                    }
                }
            }
        }
        throw lastException;
    }

    /**
     * 실제 HTTP 연결, 응답 코드 확인, 파일 쓰기를 수행하는 가장 낮은 단계의 메서드다.
     * 이 호출은 블로킹이다. 서버가 응답하는 동안 해당 워커 하나는 기다리지만 다른 워커들은
     * 각자의 다운로드를 계속할 수 있기 때문에 전체 네트워크 대기 시간이 겹쳐진다.
     */
    private void saveImageFromUrl(String imageUrl, Path output) throws IOException {
        // 실제 연결을 만들기 전에 전체 워커가 공유하는 요청 속도 제한을 통과한다.
        awaitRequestPermit();

        // 최종 파일에 바로 쓰면 중간 실패 시 깨진 jpg가 정상 파일명으로 남을 수 있다.
        // 먼저 .part에 저장하고 본문 수신이 끝난 경우에만 최종 파일명으로 이동한다.
        Path temporaryOutput = output.resolveSibling(output.getFileName() + ".part");
        Files.deleteIfExists(temporaryOutput);

        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; CardImageCrawler/1.0)");
        connection.setInstanceFollowRedirects(true);

        try {
            int statusCode = connection.getResponseCode();
            if (statusCode == 429) {
                // 서버가 제시한 재요청 대기 시간을 우선 존중한 뒤 IOException으로 재시도 루프에 전달한다.
                long retryAfterMs = parseRetryAfterMillis(connection.getHeaderField("Retry-After"));
                log.warn("Image server rate limit reached. Waiting {} ms: {}", retryAfterMs, imageUrl);
                sleepForRetry(retryAfterMs, imageUrl);
                throw new IOException("HTTP 429 for " + imageUrl);
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Unexpected HTTP status " + statusCode + " for " + imageUrl);
            }
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, temporaryOutput, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                // 같은 파일시스템에서는 가능한 경우 원자적으로 이름을 변경해 불완전한 파일 노출을 막는다.
                Files.move(temporaryOutput, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 원자적 이동을 지원하지 않는 파일시스템에서는 일반 이동으로 대체한다.
                Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
            // 실패 또는 인터럽트가 발생한 경우 남아 있을 수 있는 임시 파일을 정리한다.
            Files.deleteIfExists(temporaryOutput);
        }
    }

    /*
     * synchronized를 사용해 다운로드 워커가 nextRequestAtMillis를 동시에 읽고 갱신하지 못하게 한다.
     * 이 메서드는 동시 다운로드 수를 제한하는 것이 아니라 '새 요청의 시작 간격'을 제한한다.
     * 다운로드가 느리면 최대 네 연결이 동시에 진행될 수 있지만 요청 시작이 한 시점에 몰리지는 않는다.
     */
    private synchronized void awaitRequestPermit() throws IOException {
        /*
         * 예를 들어 다운로드 워커가 동시에 이 메서드를 호출해도 synchronized 때문에 한 번에
         * 하나만 안으로 들어온다. 첫 워커가 요청 시간을 예약하고 나가면 두 번째 워커는
         * 최소 250ms 뒤, 세 번째는 그로부터 다시 250ms 뒤에 요청을 시작한다.
         *
         * synchronized가 다운로드 전체를 감싸는 것은 아니다. 허가를 받은 워커는 이 메서드를
         * 빠져나가 실제 다운로드를 진행하므로 여러 다운로드는 여전히 동시에 진행될 수 있다.
         */
        long now = System.currentTimeMillis();
        long waitMs = nextRequestAtMillis - now;
        if (waitMs > 0) {
            sleepForRetry(waitMs, "rate limiter");
        }
        nextRequestAtMillis = System.currentTimeMillis() + REQUEST_INTERVAL_MS;
    }

    private long parseRetryAfterMillis(String retryAfter) {
        // Retry-After가 없거나 숫자 초 형식이 아니면 보수적인 기본값 1초를 사용한다.
        if (retryAfter == null || retryAfter.isBlank()) {
            return 1_000L;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.min(Math.max(seconds * 1_000L, 1_000L), MAX_RETRY_AFTER_MS);
        } catch (NumberFormatException e) {
            return 1_000L;
        }
    }

    /**
     * 대기 중 인터럽트를 받으면 인터럽트 상태를 복원하고 IOException으로 변환한다.
     * 인터럽트는 애플리케이션 종료나 작업 취소 신호일 수 있으므로 무시하면 안 된다.
     */
    private void sleepForRetry(long waitMs, String target) throws IOException {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + target, e);
        }
    }

    /*
     * record는 생성 후 필드가 바뀌지 않는 간단한 불변 데이터 운반 객체다.
     * 여러 스레드가 공유해도 내부 상태를 수정할 수 없어 작업 전달 용도로 안전하고 단순하다.
     * JPA 엔티티를 넣지 않음으로써 워커는 DB와 완전히 분리된 파일 다운로드만 담당한다.
     */
    private record ImageDownloadTask(String imageUrl, Path output) {}

}
