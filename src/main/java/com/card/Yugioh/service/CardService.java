package com.card.Yugioh.service;

import org.apache.hc.client5.http.fluent.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.net.HttpURLConnection;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {
    // sort - 카드 정렬 (atk, def, name, type, level, id, new).
    // 최신 카드 5장
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?num=10&offset=0&sort=name";
    // 금지 카드 최신순
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php?banlist=ocg&sort=new";
    // 모든 카드
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";

    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    private final AmazonS3 s3Client;
    @Value("${cloud.aws.s3.bucket}")
    private final String bucket;

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
                e.printStackTrace();
            }
        }
        return cardModels;
    }

    // private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
    //     File savePath = new File("src/main/resources/static/card_images");
    //     if (!savePath.exists()) {
    //         savePath.mkdir();
    //     }

    //     for (int i = 0; i < cardData.length(); i++) {
    //         JSONObject card = cardData.getJSONObject(i);
    //         JSONArray cardImages = card.getJSONArray("card_images");

    //         for (int j = 0; j < cardImages.length(); j++) {
    //             JSONObject imageInfo = cardImages.getJSONObject(j);
    //             String imageUrl = imageInfo.getString("image_url");
    //             Long imageId = imageInfo.getLong("id");
    //             String imageUrlSmall = imageInfo.getString("image_url_small");
    //             String imageUrlCropped = imageInfo.getString("image_url_cropped");
    //             CardImage cardImage = new CardImage(imageId, imageUrl, imageUrlSmall, imageUrlCropped, cardModels.get(i));

    //             cardImgRepository.save(cardImage);
    //             File outputFile = new File(savePath, imageId + ".jpg");

    //             if (outputFile.exists()) {
    //                 log.info("Image {} already exists, skipping download.", outputFile.getName());
    //                 continue;
    //             }

    //             saveImage(imageUrl, new File(savePath, imageId + ".jpg"));
    //         }
    //     }
    //     log.info("저장된 카드 수 : {}", cardData.length());
    // }

    private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
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

                String imageKey = imageId + ".jpg";

                if (s3Client.doesObjectExist(bucket, imageKey)) {
                    log.info("Image {} already exists in S3, skipping download.", imageKey);
                    continue;
                }

                saveImage(imageUrl, imageKey);
            }
        }
        log.info("저장된 카드 수 : {}", cardData.length());
    }

     private void saveImage(String imageUrl, String imageKey) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream()) {
            s3Client.putObject(new PutObjectRequest(bucket, imageKey, in, null));
        }
    }

    // private void saveImage(String imageUrl, File output) throws IOException {
    //     try (InputStream in = new URL(imageUrl).openStream()) {
    //         BufferedImage image = ImageIO.read(in);
    //         ImageIO.write(image, "jpg", output);
    //     }
    // }

    public void saveCardInfo(List<CardModel> cardModels) {
        for (CardModel cardModel : cardModels) {
            cardRepository.save(cardModel);
            log.info("카드 이름 : {}", cardModel.getName());
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

    public List<CardMiniDto> search(String keyWord) {
        List<CardModel> cards = cardRepository.searchByNameContaining(keyWord);
        List<CardMiniDto> cardMiniDtos = new ArrayList<>();
        for (CardModel cardModel : cards) {
            List<CardImage> cardImage = cardImgRepository.findByCardModel(cardModel);
            CardImage firstImage = cardImage.get(0);
            CardMiniDto cardMiniDto = new CardMiniDto(cardModel.getId(), cardModel.getKorName(), firstImage.getImageUrlSmall(), firstImage.getImageUrl(), cardModel.getFrameType());
            cardMiniDtos.add(cardMiniDto);
        }
        return cardMiniDtos;
    }

    // public String crawl(String cardName) {
    //     if (Pattern.matches("\\d+", cardName)) {
    //         Long cardId = (long) Integer.parseInt(cardName);
    //         CardImage cardImage = cardImgRepository.findById(cardId).orElseThrow(
    //             () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다.")
    //         );
    //         cardName = cardImage.getCardModel().getName();
    //     }
    //     try {
    //         // String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
    //         String decodedUrl = URLDecoder.decode(cardName, StandardCharsets.UTF_8.toString());
    //         String modifiedUrl = decodedUrl.replace(" ", "_");
    //         String encodedUrl = URLEncoder.encode(modifiedUrl, StandardCharsets.UTF_8.toString())
    //                                         .replace("%2F", "/")
    //                                         .replace("%3A", ":")
    //                                         .replace("%3F", "?")
    //                                         .replace("%3D", "=");
                                            


    //         String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl;

    //         // 웹 페이지의 HTML
    //         Document doc = Jsoup.connect(completeUrl).get();
    //         // 카드 정보가 있는 테이블
    //         Elements cardtableRows = doc.select("div.mw-parser-output > table.cardtable > tbody > tr");

    //         // 정확한 셀렉터를 사용하여 koreanDescription 찾기
    //         Element koreanDescription = doc.selectFirst("td.navbox-list > span[lang=ko]");

    //         if (koreanDescription != null) {
    //             // koreanDescription이 성공적으로 찾아졌을 때의 처리
    //             log.info("효과 : {}", koreanDescription.text());
    //         } else {
    //             // koreanDescription을 찾지 못했을 때의 처리
    //             log.info("한국어 설명을 찾을 수 없습니다.");
    //         }

    //         for (Element cardtableRow : cardtableRows) {
    //             // "Korean" 헤더
    //             Element header = cardtableRow.selectFirst("th.cardtablerowheader");
    //             if (header != null && header.text().equals("Korean")) {
    //                  // 한국어 이름이 있는 셀
    //                 Element koreanContent = cardtableRow.selectFirst("td.cardtablerowdata");
    //                 if (koreanContent != null) {
    //                     // 한국어 이름을 반환
    //                     String originalText = koreanContent.text();
    //                     String replaceText = originalText.replace(" Check translation", "");  
    //                     log.info("카드 한국어 이름 : {}", replaceText);
    //                     return replaceText;
    //                 }
    //             }
    //         }

    //     } catch (IOException e) {
    //         e.printStackTrace();
    //         return "데이터를 가져오는 중 오류가 발생했습니다.";
    //     }

    //     return cardName;
    // }

    public void crawlAll() {
        List<CardModel> cardModels = cardRepository.findAll();
        for (CardModel cardModel : cardModels) {
            String cardName = cardModel.getName();
            if (cardRepository.findByName(cardName).getKorName() != null) {
                continue;
            }
            try {
                // String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
                String modifiedName = cardName.replace(" ", "_").replaceAll("%(?![0-9a-fA-F]{2})", "%25");
                String decodedUrl = URLDecoder.decode(modifiedName, StandardCharsets.UTF_8.toString());
                String encodedUrl = URLEncoder.encode(decodedUrl, StandardCharsets.UTF_8.toString());
                                                    
    
                String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl;
    
                // 웹 페이지의 HTML
                Document doc = Jsoup.connect(completeUrl).get();
                // 카드 정보가 있는 테이블
                Element korName = doc.selectFirst("td.cardtablerowdata > span[lang=ko]");
                // 정확한 셀렉터를 사용하여 koreanDescription 찾기
                Element KorDesc = doc.selectFirst("td.navbox-list > span[lang=ko]");

                if (KorDesc != null) {
                    // koreanDescription이 성공적으로 찾아졌을 때의 처리
                    log.info("효과 : {}", KorDesc.text());
                    cardModel.setKorDesc(KorDesc.text());
                } else {
                    // koreanDescription을 찾지 못했을 때의 처리
                    log.info("한국어 설명을 찾을 수 없습니다.");
                    cardModel.setKorDesc(cardModel.getDesc());
                }
    
                if (korName != null) {
                    // koreanDescription이 성공적으로 찾아졌을 때의 처리
                    log.info("이름 : {}", korName.text());
                    cardModel.setKorName(korName.text());
                } else {
                    // koreanDescription을 찾지 못했을 때의 처리
                    log.info("한국어 이름을 찾을 수 없습니다.");
                    cardModel.setKorName(cardModel.getName());
                }

                cardRepository.save(cardModel);
    
            } catch (IOException e) {
                log.error("데이터를 가져오는 중 오류가 발생했습니다.", e);
            }
            
        }
    }

    public String getCardInfo(String cardName) {
        if (Pattern.matches("\\d+", cardName)) {
                Long cardId = (long) Integer.parseInt(cardName);
                CardImage cardImage = cardImgRepository.findById(cardId).orElseThrow(
                    () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다.")
                );
                cardName = cardImage.getCardModel().getKorName();
            }
        return cardName;
    }
}
