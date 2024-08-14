package com.card.Yugioh.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.card.Yugioh.dto.CardInfoDto;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.model.LimitRegulation;
import com.card.Yugioh.model.RaceEnum;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.card.Yugioh.repository.LimitRegulationRepository;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    // String apiUrl = "https://db.ygoprodeck.com/api/v7/cardinfo.php";

    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    private final LimitRegulationRepository limitRegulationRepository;
    private WebDriver driver;
    // private final AmazonS3 s3Client;
    // private String bucket;

    @PostConstruct
    public void setup() {
        // WebDriverManager를 사용하여 ChromeDriver를 자동으로 관리
        // WebDriverManager.chromedriver().setup();
        WebDriverManager.chromedriver().browserVersion("127.0.6533.120").setup();

        // 브라우저를 headless 모드로 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // 브라우저 창을 표시하지 않음
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        driver = new ChromeDriver(options);
    }

    // public void fetchAndSaveCardImages() throws IOException {
    //     String response = Request.get(apiUrl)
    //                              .execute()
    //                              .returnContent()
    //                              .asString();

    //     JSONObject jsonResponse = new JSONObject(response);
    //     JSONArray cardData = jsonResponse.getJSONArray("data");
    //     List<CardModel> cardModels = convertToCardModels(cardData);
    //     saveCardInfo(cardModels);
    //     saveCardImages(cardData, cardModels);
    // }

    // private static List<CardModel> convertToCardModels(JSONArray cardData) {
    //     List<CardModel> cardModels = new ArrayList<>();
    //     // JSON 문자열을 Java 객체로 변환
    //     ObjectMapper objectMapper = new ObjectMapper();
    //     for (int i = 0; i < cardData.length(); i++) {
    //         JSONObject cardJson = cardData.getJSONObject(i);
    //         try {
    //             // ObjectMapper.readValue() 메소드를 사용하여 JSON 문자열을 CardModel 클래스의 인스턴스로 변환
    //             CardModel cardModel = objectMapper.readValue(cardJson.toString(), CardModel.class);
    //             cardModels.add(cardModel);
    //         } catch (IOException e) {
    //             log.error("JSON을 CardModel로 변환하는 중 오류가 발생했습니다.", e);
    //         }
    //     }
    //     return cardModels;
    // }

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

    // private void saveCardImages(JSONArray cardData, List<CardModel> cardModels) throws IOException {
    //     for (int i = 0; i < cardData.length(); i++) {
    //         JSONObject card = cardData.getJSONObject(i);
    //         JSONArray cardImages = card.getJSONArray("card_images");

    //         for (int j = 0; j < cardImages.length(); j++) {
    //             JSONObject imageInfo = cardImages.getJSONObject(j);
    //             String imageUrl = imageInfo.getString("image_url");
    //             Long imageId = imageInfo.getLong("id");
    //             if (cardImgRepository.existsById(imageId)) {
    //                 log.info("이미지 {} 는 DB에 이미 존재합니다. 다운로드를 건너뜁니다.", imageId);
    //                 continue;
    //             }
    //             String imageUrlSmall = imageInfo.getString("image_url_small");
    //             String imageUrlCropped = imageInfo.getString("image_url_cropped");
    //             CardImage cardImage = new CardImage(imageId, imageUrl, imageUrlSmall, imageUrlCropped, cardModels.get(i));
    //             cardImgRepository.save(cardImage);
    //             String imageKey = imageId + ".jpg";
    //             if (s3Client.doesObjectExist(bucket, imageKey)) {
    //                 log.info("이미지 {} 는 S3에 이미 존재합니다. 다운로드를 건너뜁니다.", imageKey);
    //                 continue;
    //             }

    //             saveImage(imageUrl, imageKey);
    //         }
    //     }
    //     log.info("저장된 카드 수 : {}", cardData.length());
    // }

    //  private void saveImage(String imageUrl, String imageKey) throws IOException {
    //     URL url = new URL(imageUrl);
    //     HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    //     connection.setRequestMethod("GET");
    //     try (InputStream in = connection.getInputStream()) {
    //         s3Client.putObject(new PutObjectRequest(bucket, imageKey, in, null));
    //     }
    // }

    // private void saveImage(String imageUrl, File output) throws IOException {
    //     try (InputStream in = new URL(imageUrl).openStream()) {
    //         BufferedImage image = ImageIO.read(in);
    //         ImageIO.write(image, "jpg", output);
    //     }
    // }

    // public void saveCardInfo(List<CardModel> cardModels) {
    //     for (CardModel cardModel : cardModels) {
    //         if (cardRepository.existsById(cardModel.getId())) {
    //             log.info("카드 {} 는 DB에 이미 존재합니다. 저장을 건너뜁니다.", cardModel.getName());
    //             continue;
    //         }
    //         cardRepository.save(cardModel);
    //         log.info("카드 이름 : {}", cardModel.getName());
    //     }
    // }

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

    public Page<CardMiniDto> search(String keyWord, Pageable pageable) {
        Page<CardModel> cards = cardRepository.searchByNameContaining(keyWord, pageable);
        return cards.map(cardModel -> {
            List<CardImage> cardImage = cardImgRepository.findByCardModel(cardModel);
            CardImage firstImage = cardImage.get(0);
            return new CardMiniDto(cardModel.getId(), cardModel.getKorName(), firstImage.getImageUrlSmall(), firstImage.getImageUrl(), cardModel.getFrameType());
        });
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
    //          String modifiedName = cardName.replace(" ", "_").replaceAll("%(?![0-9a-fA-F]{2})", "%25");
    //          String encodedUrl = URLEncoder.encode(modifiedName, StandardCharsets.UTF_8.toString());
    //          String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl;

    //         // 웹 페이지의 HTML
    //         Document doc = Jsoup.connect(completeUrl).get();
    //         // 카드 정보가 있는 테이블
    //         Elements cardtableRows = doc.select("div.mw-parser-output > table.cardtable > tbody > tr");
    //         // 카드 정보가 있는 테이블을 순회
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
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime oneweekAgo = today.minus(7, ChronoUnit.DAYS);
        List<CardModel> cardModels = cardRepository.findByCreatedAtAfter(oneweekAgo);

        for (CardModel cardModel : cardModels) {
            String cardName = cardModel.getName();
            if (cardRepository.findByName(cardName).getKorName() != null && cardRepository.findByName(cardName).getKorDesc() != null) {
                continue;
            }
            try {
                // 띄워쓰기를 _로 변경, 인코딩 오류를 발생하는 % 처리
                String modifiedName = cardName.replace(" ", "_").replaceAll("%(?![0-9a-fA-F]{2})", "%25");
                String encodedUrl = URLEncoder.encode(modifiedName, StandardCharsets.UTF_8.toString());
                                                    
    
                String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl;
    
                // 웹 페이지의 HTML
                Document doc = Jsoup.connect(completeUrl).get();
                // 카드 정보가 있는 테이블
                Element korName = doc.selectFirst("td.cardtablerowdata > span[lang=ko]");
                Element korDesc = doc.selectFirst("td.navbox-list > span[lang=ko]");
                if (korDesc != null) {
                    // koreanDescription이 성공적으로 찾아졌을 때의 처리
                    log.info("효과 : {}", korDesc.text());
                    cardModel.setKorDesc(korDesc.text());
                } else {
                    // 펜듈럼 카드일 경우
                    Elements PendulumKorDescs = doc.select("td.navbox-list dd > span[lang=ko]");
                    StringBuilder combinedKorDesc = new StringBuilder();
                    for (Element PendulumKorDesc : PendulumKorDescs) {
                        if (PendulumKorDesc != null) {
                            log.info("효과 : {}", PendulumKorDesc.text());
                            combinedKorDesc.append(PendulumKorDesc.text()).append("\n");
                        }
                    }

                    if (combinedKorDesc.length() > 0) {
                        cardModel.setKorDesc(combinedKorDesc.toString().trim());
                    } else {
                        log.info("한국어 설명을 찾을 수 없습니다.");
                    }
                }
                if (korName != null) {
                    // koreanDescription이 성공적으로 찾아졌을 때의 처리
                    log.info("이름 : {}", korName.text());
                    cardModel.setKorName(korName.text());
                } else {
                    // koreanDescription을 찾지 못했을 때의 처리
                    log.info("한국어 이름을 찾을 수 없습니다.");
                }

                cardRepository.save(cardModel);
    
            } catch (IOException e) {
                log.error("데이터를 가져오는 중 오류가 발생했습니다.", e);
            }
            
        }
    }

    public CardInfoDto getCardInfo(String cardName) {
        String korDesc = "";
        String restrictionType = "unlimited";
        if (Pattern.matches("\\d+", cardName)) {
                Long cardId = (long) Integer.parseInt(cardName);
                CardImage cardImage = cardImgRepository.findById(cardId).orElseThrow(
                    () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다.")
                );
                if (cardImage.getCardModel().getKorName() != null) cardName = cardImage.getCardModel().getKorName();
            }
            CardModel cardModel = cardRepository.findByKorName(cardName).orElseThrow(
                () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다.")
            );
            if (cardModel.getKorDesc() == null) {
                korDesc = cardModel.getDesc();
            } else {
                korDesc = cardModel.getKorDesc();
            }
        String enRace = cardModel.getRace();
        RaceEnum korRace = RaceEnum.valueOf(enRace);
        LimitRegulation limitRegulation = limitRegulationRepository.findByCardName(cardName);
        if (limitRegulation != null) {
            restrictionType = limitRegulation.getRestrictionType();
        }
        return new CardInfoDto(cardName, korDesc, korRace.getRace(), restrictionType);
    }

    public void limitCrawl() {
        limitRegulationRepository.deleteAll();
        try {
             // 웹 페이지 열기
            driver.get("https://ygoprodeck.com/banlist/");

            Thread.sleep(2000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement banlistTypeElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("banlisttype")));
            // 'banlisttype'을 'Master Duel'로 설정
            Select banlistTypeSelect = new Select(banlistTypeElement);
            banlistTypeSelect.selectByValue("Master Duel");

            // 'banlistdate'를 최신 날짜로 설정
            WebElement banlistDateElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("banlistdate")));
            Select banlistDateSelect = new Select(banlistDateElement);
            banlistDateSelect.selectByIndex(0);  // 최신 항목을 선택

            // 'textView' 버튼 클릭
            WebElement textView = wait.until(ExpectedConditions.elementToBeClickable(By.id("textButton")));
            textView.click();

             // 데이터를 가져올 시간을 확보하기 위해 잠시 대기
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("banned")));

            scrapeListData("banned");
            scrapeListData("limited");
            scrapeListData("semilimited");

        } catch (Exception e) {
            log.error("데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
        } finally {
            // 브라우저 닫기
            driver.quit();
        }
    }

    private void scrapeListData(String listId) {
        try {
            WebElement listElement = driver.findElement(By.id(listId));
            List<WebElement> spans = listElement.findElements(By.xpath(".//span"));
            for (WebElement span : spans) {
                List<WebElement> strongElements = span.findElements(By.xpath(".//span[1]/span[1]/a/strong"));
                for (WebElement strong : strongElements) {
                    LimitRegulation limitRegulation = new LimitRegulation();
                    limitRegulation.setCardName(strong.getText());
                    limitRegulation.setRestrictionType("listId");
                    limitRegulationRepository.save(limitRegulation);
                }
            }
        } catch (Exception e) {
            log.error("리스트 " + listId + " 데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
        }
    }
}
