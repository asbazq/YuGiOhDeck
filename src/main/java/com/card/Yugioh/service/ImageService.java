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
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImageService {
    /*
     * ========================================================================
     * 이 클래스의 병렬 다운로드 구조를 처음 보는 사람을 위한 전체 설명
     * ========================================================================
     *
     * 1. 스레드란?
     *    하나의 프로그램 안에서 코드를 실행하는 작업 흐름이다. 관리자 API 또는 스케줄러가
     *    fetchAndSaveCardImages를 호출하면 최초에는 그 호출을 담당하는 스레드 하나만 존재한다.
     *    이 주석에서는 그 스레드를 '호출 스레드'라고 부른다.
     *
     * 2. 기존 순차 방식은 어떻게 동작했는가?
     *
     *    호출 스레드: [큰 이미지 A 완료 대기] -> [작은 이미지 A 완료 대기]
     *              -> [큰 이미지 B 완료 대기] -> [작은 이미지 B 완료 대기]
     *
     *    HTTP 다운로드는 서버가 응답할 때까지 현재 스레드가 기다리는 블로킹 I/O다.
     *    이미지 하나를 기다리는 동안 다음 이미지를 시작하지 못하므로 네트워크 대기 시간이
     *    모두 합산된다.
     *
     * 3. 현재 병렬 방식은 어떻게 동작하는가?
     *
     *    호출 스레드: API/DB 처리 -> 다운로드 작업 목록 생성 -> invokeAll에서 완료 대기
     *                                           |
     *                                           +-> 워커 1: 이미지 A 다운로드
     *                                           +-> 워커 2: 이미지 B 다운로드
     *                                           +-> 워커 3: 이미지 C 다운로드
     *                                           +-> 워커 4: 이미지 D 다운로드
     *
     *    워커(worker)는 호출 스레드 대신 실제 다운로드를 수행하는 보조 스레드다.
     *    네 워커가 네트워크 대기 시간을 서로 겹치게 만들어 전체 실행 시간을 단축한다.
     *
     * 4. 작업이 이미지 100개인데 워커가 4개뿐이면?
     *    ExecutorService 내부 작업 큐에 100개의 Callable이 들어간다. 처음 네 개만 실행되고,
     *    워커 하나가 작업을 끝낼 때마다 큐에서 다음 작업 하나를 가져간다. 따라서 이미지가
     *    많아져도 스레드가 100개 생성되지 않는다.
     *
     * 5. 이것은 완전한 비동기 API인가?
     *    아니다. 다운로드 작업은 별도 워커에서 병렬 실행되지만 invokeAll이 모든 다운로드의
     *    완료를 기다린다. 따라서 fetchAndSaveCardImages를 호출한 관리자 API도 최종 완료까지
     *    기다린다. 정확한 표현은 '동기 메서드 내부의 멀티스레드 병렬 처리'다.
     *
     * 6. 왜 DB 작업까지 워커에서 실행하지 않는가?
     *    JPA EntityManager와 영속성 컨텍스트는 스레드 안전하지 않다. 같은 엔티티를 여러
     *    스레드에서 조회/변경/저장하면 상태 충돌이나 예측하기 어려운 예외가 발생할 수 있다.
     *    그래서 DB 처리는 호출 스레드가 담당하고 워커에는 URL과 파일 경로만 전달한다.
     *
     * 7. 빠르게만 요청하면 대상 서버에 부담이 되지 않는가?
     *    네 워커를 사용하되 모든 워커가 공유하는 속도 제한기를 통과하게 한다. 요청 시작
     *    시점은 최소 250ms 간격이고, 429 응답을 받으면 서버의 Retry-After를 존중한다.
     */
    // 동시에 실행할 다운로드 수. 무제한 스레드 생성을 막고 대상 서버 부하를 제한한다.
    private static final int DOWNLOAD_CONCURRENCY = 4;
    // 연결 수립과 응답 본문 읽기가 무한정 멈추지 않도록 각각 제한 시간을 둔다.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    // 일시적인 네트워크 오류나 5xx/429 응답에 대응하기 위한 파일별 최대 시도 횟수다.
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    // 네 워커가 있더라도 HTTP 요청 시작 시점은 전체 기준 최소 250ms 간격으로 직렬화한다.
    // 즉, 이 애플리케이션 인스턴스가 새 요청을 시작하는 속도는 최대 약 4회/초다.
    private static final long REQUEST_INTERVAL_MS = 250L;
    // 서버의 Retry-After 값이 지나치게 커 작업이 장시간 정지하는 것을 막는 상한이다.
    private static final long MAX_RETRY_AFTER_MS = 60_000L;
    // 스케줄러 실행과 관리자 수동 실행이 겹쳐 동일 이미지를 중복 다운로드하는 것을 막는다.
    // compareAndSet으로 확인과 상태 변경을 하나의 원자적 연산으로 처리한다.
    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    // 모든 다운로드 워커가 공유하는 다음 요청 허용 시각. synchronized 메서드 안에서만 갱신한다.
    private long nextRequestAtMillis;
    // sort - 카드 정렬 (atk, def, name, type, level, id, new).
    // 최신 카드 5장
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=5&offset=0&sort=new";
    // 금지 카드 최신순
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?banlist=ocg&sort=new";
    // 모든 카드
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=500&offset=0&sort=new";

    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    // private final Path savePath = Paths.get("D:/project/card_images");

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

    /**
     * 카드 API를 읽고 카드/이미지 메타데이터와 실제 이미지 파일을 저장한다.
     *
     * 실행 스레드:
     * - API JSON 요청과 DB 저장: 이 메서드를 호출한 스레드
     * - 이미지 파일 다운로드: downloadImages가 만든 네 개의 워커 스레드
     *
     * 반환 시점:
     * - 모든 이미지 다운로드가 성공 또는 최종 실패로 끝난 뒤 반환한다.
     * - 따라서 반환 타입이 Future가 아니며 호출자 관점에서는 동기 메서드다.
     *
     * @param apiUrl 카드 목록을 반환하는 YGOPRODeck API 주소
     * @return API 응답의 카드 개수. 이미지 다운로드 성공 개수와는 다르다.
     * @throws IOException API 목록 요청이나 저장 디렉터리 처리 자체가 실패한 경우
     */
    public int fetchAndSaveCardImages(String apiUrl) throws IOException {
        // false -> true 변경에 성공한 호출 하나만 작업을 시작할 수 있다.
        if (!fetchInProgress.compareAndSet(false, true)) {
            throw new ImageFetchAlreadyRunningException();
        }
        try {
            // 1. 카드 API JSON은 한 번만 동기로 요청한다.
            String response = Request.get(apiUrl)
                                     .execute()
                                     .returnContent()
                                     .asString();

            JSONObject jsonResponse = new JSONObject(response);
            JSONArray cardData = jsonResponse.getJSONArray("data");

            // 2. JSON을 엔티티로 변환하고 카드 정보를 먼저 저장한다.
            // 이미지 엔티티가 CardModel을 외래 키로 참조하므로 카드 저장이 선행되어야 한다.
            List<CardModel> cardModels = convertToCardModels(cardData);
            saveCardInfo(cardModels);

            // 3. 이미지 메타데이터는 단일 스레드에서 DB에 저장하고 실제 파일만 병렬 다운로드한다.
            saveCardImages(cardData, cardModels);
            return cardData.length();
        } finally {
            // 성공, 예외, 인터럽트 여부와 관계없이 잠금을 해제해 다음 실행이 가능하게 한다.
            fetchInProgress.set(false);
        }
    }

    private static List<CardModel> convertToCardModels(JSONArray cardData) throws IOException {
        List<CardModel> cardModels = new ArrayList<>();
        // JSON 문자열을 Java 객체로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < cardData.length(); i++) {
            JSONObject cardJson = cardData.getJSONObject(i);
            try {
                // ObjectMapper.readValue() 메소드를 사용하여 JSON 문자열을 CardModel 클래스의 인스턴스로 변환
                CardModel cardModel = objectMapper.readValue(cardJson.toString(), CardModel.class);
                if (cardModel.getId() == null || cardModel.getName() == null || cardModel.getName().isBlank()) {
                    throw new IOException("Card id and name are required");
                }
                cardModels.add(cardModel);
            } catch (IOException e) {
                // 이미지 JSON과 모델 목록은 같은 순서를 사용하므로 실패한 행을 건너뛰면 안 된다.
                // 모든 카드가 검증되기 전에 DB에 쓰지 않도록 호출자에게 실패를 전달한다.
                throw new IOException("Invalid card data at index " + i, e);
            }
        }
        return cardModels;
    }

    /**
     * 이미지 DB 행을 저장하고, 로컬에 없는 파일을 다운로드 작업 목록으로 만든다.
     *
     * 이 메서드의 반복문 안에서는 다운로드하지 않는다. 반복문 안에서 바로 다운로드하면
     * 다시 한 장씩 기다리는 순차 방식이 되기 때문이다. 먼저 모든 ImageDownloadTask를
     * 수집하고 마지막에 한 번 downloadImages에 넘긴다.
     */
    private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
        // 워커 스레드에 JPA 엔티티를 넘기지 않기 위해 URL과 출력 경로만 작업 목록에 담는다.
        List<ImageDownloadTask> downloadTasks = new ArrayList<>();
        // Path savePath = Paths.get(System.getProperty("user.home"), "Desktop", "yugioh", "card_images");
        if (Files.notExists(savePath)) {
            log.info("Directory {} does not exist. Creating now...", savePath.toString());
            Files.createDirectories(savePath);
        } else {
            log.info("Directory {} already exists.", savePath.toString());
        }

        if (Files.notExists(saveSmallPath)) {
            log.info("Directory {} does not exist. Creating now...", saveSmallPath.toString());
            Files.createDirectories(saveSmallPath);
        } else {
            log.info("Directory {} already exists.", saveSmallPath.toString());
        }

        for (int i = 0; i < cardData.length(); i++) {
            JSONObject card = cardData.getJSONObject(i);
            JSONArray cardImages = card.getJSONArray("card_images");

            CardModel baseModel = cardModels.get(i);

            // ID 또는 이름으로 기존 모델을 가져오고 모델이 없는 경우 해당 모델을 유지 -> 이미지를 저장할 때 외래 키 위반 방지
            CardModel referenceModel = cardRepository.findById(baseModel.getId())
                .orElseGet(() -> {
                    CardModel byName = cardRepository.findByName(baseModel.getName()).orElse(null);
                    return byName != null ? byName : cardRepository.saveAndFlush(baseModel);
                });

            for (int j = 0; j < cardImages.length(); j++) {
                JSONObject imageInfo = cardImages.getJSONObject(j);
                String imageUrl = imageInfo.getString("image_url");
                Long imageId = imageInfo.getLong("id");
                String imageUrlSmall = imageInfo.getString("image_url_small");
                String imageUrlCropped = imageInfo.getString("image_url_cropped");
                CardImage cardImage = new CardImage(imageId, imageUrl, imageUrlSmall, imageUrlCropped, referenceModel);

                // DB 작업은 현재 호출 스레드에서 수행한다. JPA EntityManager는 스레드 안전하지 않다.
                cardImgRepository.save(cardImage);
                Path outputFile = savePath.resolve(imageId + ".jpg");
                Path smallOut = saveSmallPath.resolve(imageId + ".jpg");
                // File outputFile = new File(savePath, imageId + ".jpg");

                // 파일이 없는 경우에만 작업을 예약한다. 여기서는 아직 네트워크 요청을 보내지 않는다.
                if (Files.notExists(outputFile)) {
                    downloadTasks.add(new ImageDownloadTask(imageUrl, outputFile));
                } else {
                    log.info("Large image {} exists. Skip.", outputFile.getFileName());
                }

                // 원본과 작은 이미지를 각각 독립 작업으로 만들어 같은 스레드 풀에서 처리한다.
                if (Files.notExists(smallOut)) {
                    downloadTasks.add(new ImageDownloadTask(imageUrlSmall, smallOut));
                } else {
                    log.info("Small image {} exists. Skip.", smallOut.getFileName());
                }

            }
        }
        // DB 관련 반복이 모두 끝난 후 순수 파일 다운로드 작업만 병렬로 실행한다.
        downloadImages(downloadTasks);
        log.info("저장된 카드 수 : {}", cardData.length());
    }

    /**
     * 수집된 작업들을 고정 크기 스레드 풀에서 병렬 실행한다.
     *
     * 주요 타입:
     * - ExecutorService: 워커 스레드와 작업 대기 큐를 관리하는 실행 관리자
     * - Callable<Path>: 실행할 다운로드 한 건. 성공하면 Path를 반환하고 실패하면 예외 발생
     * - Future<Path>: 아직 끝나지 않았을 수도 있는 Callable의 미래 결과를 나타내는 손잡이
     *
     * invokeAll 동작 예시(tasks가 10개, 워커가 4개인 경우):
     * - 1차: 작업 1~4 실행, 작업 5~10은 큐에서 대기
     * - 워커 2가 작업 2 완료: 같은 워커가 작업 5 실행
     * - 이런 방식으로 큐가 빌 때까지 반복
     * - 작업 10까지 모두 끝나야 invokeAll이 반환
     *
     * @param tasks URL과 출력 경로만 가진 불변 다운로드 작업 목록
     */
    private void downloadImages(List<ImageDownloadTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }

        // 작업 수와 관계없이 워커를 네 개로 고정한다. 남은 작업은 Executor 내부 큐에서 대기한다.
        ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_CONCURRENCY);
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image downloads interrupted. Remaining downloads were cancelled.");
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
     * synchronized를 사용해 네 워커가 nextRequestAtMillis를 동시에 읽고 갱신하지 못하게 한다.
     * 이 메서드는 동시 다운로드 수를 제한하는 것이 아니라 '새 요청의 시작 간격'을 제한한다.
     * 다운로드가 느리면 최대 네 연결이 동시에 진행될 수 있지만 요청 시작이 한 시점에 몰리지는 않는다.
     */
    private synchronized void awaitRequestPermit() throws IOException {
        /*
         * 예를 들어 네 워커가 동시에 이 메서드를 호출해도 synchronized 때문에 한 번에
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

    public void saveCardInfo(List<CardModel> cardModels) {
        for (CardModel cardModel : cardModels) {
            if (cardRepository.existsById(cardModel.getId()) || cardRepository.existsByName(cardModel.getName())) {
                log.info("카드 {} 는 DB에 이미 존재합니다. 저장을 건너뜁니다.", cardModel.getName());
                continue;
            }
            cardRepository.save(cardModel);
            log.info("카드 이름 : {}", cardModel.getName());
        }
    }
}
