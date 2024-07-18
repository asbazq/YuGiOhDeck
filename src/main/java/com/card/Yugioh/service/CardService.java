package com.card.Yugioh.service;

import org.apache.hc.client5.http.fluent.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CardService {
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?name=Dark%20Magician";
    // sort - 카드 정렬 (atk, def, name, type, level, id, new).
    // 최신 카드 5장
    String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=100&offset=0&sort=name";
    // 금지 카드 최신순
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?banlist=ocg&sort=new";
    // 모든 카드
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;

    public CardService(CardRepository cardRepository, CardImgRepository cardImgRepository) {
        this.cardRepository = cardRepository;
        this.cardImgRepository = cardImgRepository;
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

    private static List<CardModel> convertToCardModels(JSONArray cardDataArray) {
        List<CardModel> cardModels = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < cardDataArray.length(); i++) {
            JSONObject cardJson = cardDataArray.getJSONObject(i);
            try {
                CardModel cardModel = objectMapper.readValue(cardJson.toString(), CardModel.class);
                cardModels.add(cardModel);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cardModels;
    }

    private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
        File savePath = new File("src/main/resources/static/card_images");
        if (!savePath.exists()) {
            savePath.mkdir();
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
                File outputFile = new File(savePath, imageId + ".jpg");

                if (outputFile.exists()) {
                    log.info("Image {} already exists, skipping download.", outputFile.getName());
                    continue;
                }

                saveImage(imageUrl, new File(savePath, imageId + ".jpg"));
            }
        }
        log.info("저장된 카드 수 : {}", cardData.length());
    }

    private void saveImage(String imageUrl, File output) throws IOException {
        try (InputStream in = new URL(imageUrl).openStream()) {
            BufferedImage image = ImageIO.read(in);
            ImageIO.write(image, "jpg", output);
        }
    }

    // public List<String> getCardImageUrls() {
    //     List<String> imageUrls = new ArrayList<>();
    //     File savePath = new File("src/main/resources/static/card_images");

    //     if (savePath.exists() && savePath.isDirectory()) {
    //         File[] files = savePath.listFiles();
    //         if (files != null) {
    //             for (File file : files) {
    //                 if (file.isFile()) {
    //                     log.info(file.getName());
    //                     imageUrls.add("/card_images/" + file.getName());
    //                 }
    //             }
    //         }
    //     }
    //     log.info("조회 카드 수 : {}", imageUrls.size());
        
    //     return imageUrls;
    // }

    public void saveCardInfo(List<CardModel> cardModels) {
        for (CardModel cardModel : cardModels) {
            cardRepository.save(cardModel);
            log.info("카드 이름 : {}", cardModel.getName());
        }
    
}

    public List<CardMiniDto> search(String keyWord) {
        List<CardModel> cards = cardRepository.searchByNameContaining(keyWord);
        List<CardMiniDto> cardMiniDtos = new ArrayList<>();
        for (CardModel cardModel : cards) {
            List<CardImage> cardImage = cardImgRepository.findByCardModel(cardModel);
            CardImage firstImage = cardImage.get(0);
            CardMiniDto cardMiniDto = new CardMiniDto(cardModel.getId(), cardModel.getName(), firstImage.getImageUrlSmall(), firstImage.getImageUrl(), cardModel.getFrameType());
            cardMiniDtos.add(cardMiniDto);
        }
        for (CardMiniDto cardMiniDto : cardMiniDtos) {
            log.info(cardMiniDto.getImageUrlSmall());
        }
        return cardMiniDtos;
    }
}
