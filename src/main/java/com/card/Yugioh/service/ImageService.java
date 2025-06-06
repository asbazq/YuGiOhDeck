package com.card.Yugioh.service;

import org.apache.hc.client5.http.fluent.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ImageService {
    String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=100&offset=0&sort=new";

    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    private final Path savePath = Paths.get("D:/project/card_images");
    // /home/d568/Desktop/yugioh/card_images

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

        for (int i = 0; i < cardData.length(); i++) {
            JSONObject card = cardData.getJSONObject(i);
            JSONArray cardImages = card.getJSONArray("card_images");

            for (int j = 0; j < cardImages.length(); j++) {
                JSONObject imageInfo = cardImages.getJSONObject(j);
                String imageUrl = imageInfo.getString("image_url");
                Long imageId = imageInfo.getLong("id");
                String imageUrlSmall = imageInfo.getString("image_url_small");
                String imageUrlCropped = imageInfo.getString("image_url_cropped");
                CardImage cardImage = new CardImage(imageId, imageUrl, imageUrlSmall, imageUrlCropped, cardModels.get(i));

                cardImgRepository.save(cardImage);
                Path outputFile = savePath.resolve(imageId + ".jpg");
                // File outputFile = new File(savePath, imageId + ".jpg");

                if (Files.exists(outputFile)) {
                    log.info("Image {} already exists, skipping download.", outputFile.getFileName());
                    continue;
                }

                saveImage(imageUrl, outputFile);
            }
        }
        log.info("저장된 카드 수 : {}", cardData.length());
    }

    private void saveImage(String imageUrl, Path output) throws IOException {
        try (InputStream in = new URL(imageUrl).openStream()) {
            BufferedImage image = ImageIO.read(in);
            Files.createDirectories(output.getParent()); // 부모 디렉토리가 없으면 생성
            ImageIO.write(image, "jpg", output.toFile());
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
    
    // image 조회
    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable("filename") String filename) {
        try {
            Path imagePath = savePath.resolve(filename);
            Resource resource = new UrlResource(imagePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch(MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}