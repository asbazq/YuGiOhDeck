package com.card.Yugioh.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


import org.apache.hc.client5.http.fluent.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.web.bind.annotation.RestController;

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
@RestController
public class ImageService {
    // sort - 카드 정렬 (atk, def, name, type, level, id, new).
    // 최신 카드 5장
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=5&offset=0&sort=new";
    // 금지 카드 최신순
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?banlist=ocg&sort=new";
    // 모든 카드
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=500&offset=0&sort=new";

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

    public void fetchAndSaveCardImages() throws IOException {
        String response = Request.get(apiUrl)
                                 .execute()
                                 .returnContent()
                                 .asString();

        JSONObject jsonResponse = new JSONObject(response);
        JSONArray cardData = jsonResponse.getJSONArray("data");
        List<CardModel> cardModels = convertToCardModels(cardData);
        saveCardInfo(cardModels);
        saveCardImages(cardData, cardModels);
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
                    CardModel byName = cardRepository.findByName(baseModel.getName());
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
                    saveImageFromUrl(imageUrl, outputFile);
                } else {
                    log.info("Large image {} exists. Skip.", outputFile.getFileName());
                }

                // 작은 이미지 저장
                if (Files.notExists(smallOut)) {
                    saveImageFromUrl(imageUrlSmall, smallOut);
                } else {
                    log.info("Small image {} exists. Skip.", smallOut.getFileName());
                }

            }
        }
        log.info("저장된 카드 수 : {}", cardData.length());
    }

    private void saveImageFromUrl(String imageUrl, Path output) throws IOException {
        try (InputStream in = new URL(imageUrl).openStream()) {
            Files.copy(in, output);
        }
    }

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