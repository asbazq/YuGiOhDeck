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
    private static final int DOWNLOAD_CONCURRENCY = 4;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final long REQUEST_INTERVAL_MS = 250L;
    private static final long MAX_RETRY_AFTER_MS = 60_000L;
    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
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

    public int fetchAndSaveCardImages(String apiUrl) throws IOException {
        if (!fetchInProgress.compareAndSet(false, true)) {
            throw new ImageFetchAlreadyRunningException();
        }
        try {
            String response = Request.get(apiUrl)
                                     .execute()
                                     .returnContent()
                                     .asString();

            JSONObject jsonResponse = new JSONObject(response);
            JSONArray cardData = jsonResponse.getJSONArray("data");
            List<CardModel> cardModels = convertToCardModels(cardData);
            saveCardInfo(cardModels);
            saveCardImages(cardData, cardModels);
            return cardData.length();
        } finally {
            fetchInProgress.set(false);
        }
    }

    private static List<CardModel> convertToCardModels(JSONArray cardData) {
        List<CardModel> cardModels = new ArrayList<>();
        // JSON 문자열을 Java 객체로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < cardData.length(); i++) {
            JSONObject cardJson = cardData.getJSONObject(i);
            try {
                // ObjectMapper.readValue() 메소드를 사용하여 JSON 문자열을 CardModel 클래스의 인스턴스로 변환
                CardModel cardModel = objectMapper.readValue(cardJson.toString(), CardModel.class);
                cardModels.add(cardModel);
            } catch (IOException e) {
                log.error("JSON을 CardModel로 변환하는 중 오류가 발생했습니다.", e);
            }
        }
        return cardModels;
    }

    private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
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

                cardImgRepository.save(cardImage);
                Path outputFile = savePath.resolve(imageId + ".jpg");
                Path smallOut = saveSmallPath.resolve(imageId + ".jpg");
                // File outputFile = new File(savePath, imageId + ".jpg");

                 // 큰 이미지 저장
                if (Files.notExists(outputFile)) {
                    downloadTasks.add(new ImageDownloadTask(imageUrl, outputFile));
                } else {
                    log.info("Large image {} exists. Skip.", outputFile.getFileName());
                }

                // 작은 이미지 저장
                if (Files.notExists(smallOut)) {
                    downloadTasks.add(new ImageDownloadTask(imageUrlSmall, smallOut));
                } else {
                    log.info("Small image {} exists. Skip.", smallOut.getFileName());
                }

            }
        }
        downloadImages(downloadTasks);
        log.info("저장된 카드 수 : {}", cardData.length());
    }

    private void downloadImages(List<ImageDownloadTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_CONCURRENCY);
        try {
            List<Callable<Path>> jobs = tasks.stream()
                .<Callable<Path>>map(task -> () -> {
                    saveImageWithRetry(task.imageUrl(), task.output());
                    return task.output();
                })
                .toList();

            List<Future<Path>> futures = executor.invokeAll(jobs);
            int successCount = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get();
                    successCount++;
                } catch (ExecutionException e) {
                    log.error("Image download failed after retries: {}", tasks.get(i).imageUrl(), e.getCause());
                }
            }
            log.info("Image downloads complete. success={}, failed={}", successCount, tasks.size() - successCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image downloads interrupted. Remaining downloads were cancelled.");
        } finally {
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

    private void saveImageWithRetry(String imageUrl, Path output) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                saveImageFromUrl(imageUrl, output);
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
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

    private void saveImageFromUrl(String imageUrl, Path output) throws IOException {
        awaitRequestPermit();
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
                Files.move(temporaryOutput, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
            Files.deleteIfExists(temporaryOutput);
        }
    }

    private synchronized void awaitRequestPermit() throws IOException {
        long now = System.currentTimeMillis();
        long waitMs = nextRequestAtMillis - now;
        if (waitMs > 0) {
            sleepForRetry(waitMs, "rate limiter");
        }
        nextRequestAtMillis = System.currentTimeMillis() + REQUEST_INTERVAL_MS;
    }

    private long parseRetryAfterMillis(String retryAfter) {
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

    private void sleepForRetry(long waitMs, String target) throws IOException {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + target, e);
        }
    }

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
    
    // // 원본 이미지 조회
    // @GetMapping("/images/{filename}")
    // public ResponseEntity<Resource> getImage(@PathVariable("filename") String filename) {
    //     return serveLocalFile(savePath.resolve(filename));
    // }

    // // 작은 이미지 조회
    // @GetMapping("/images/small/{filename}")
    // public ResponseEntity<Resource> getSmallImage(@PathVariable("filename") String filename) {
    //     return serveLocalFile(saveSmallPath.resolve(filename));
    // }

    // private ResponseEntity<Resource> serveLocalFile(Path imagePath) {
    //     try {
    //         Resource resource = new UrlResource(imagePath.toUri());
    //         if (resource.exists() && resource.isReadable()) {
    //             return ResponseEntity.ok()
    //                 .header(HttpHeaders.CONTENT_DISPOSITION,
    //                         "inline; filename=\"" + resource.getFilename() + "\"")
    //                 .body(resource);
    //         }
    //         return ResponseEntity.notFound().build();
    //     } catch (MalformedURLException e) {
    //         return ResponseEntity.badRequest().build();
    //     }
    // }

}
