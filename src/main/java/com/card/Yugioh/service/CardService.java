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
import org.springframework.transaction.annotation.Transactional;

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
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {


    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    private final LimitRegulationRepository limitRegulationRepository;
    private WebDriver driver;
    // private final AmazonS3 s3Client;
    // private String bucket;

    private static final Set<String> PENDULUM_FRAMES = Set.of(
        "effect_pendulum", "xyz_pendulum",
        "synchro_pendulum", "fusion_pendulum",
        "normal_pendulum"
    );

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


    public Page<CardMiniDto> search(String keyWord, Pageable pageable) {
        Page<CardModel> cards = cardRepository.searchByNameContaining(keyWord, pageable);
        return cards.map(cardModel -> {
            List<CardImage> cardImages = cardImgRepository.findByCardModel(cardModel);
            LimitRegulation limit = limitRegulationRepository.findByCardName(cardModel.getName());
            if (limit == null && cardModel.getKorName() != null) {
                limit = limitRegulationRepository.findByCardName(cardModel.getKorName());
            }
            String restrictionType = limit != null ? limit.getRestrictionType() : "unlimited";
            String displayName = cardModel.getKorName() != null ? 
                                 cardModel.getKorName() : cardModel.getName();
            if (cardImages.isEmpty()) {
                return new CardMiniDto(
                    cardModel.getId(),
                    displayName,
                    "",
                    "",
                    cardModel.getFrameType(),
                    restrictionType
                );
            }
            CardImage firstImage = cardImages.get(0);
            return new CardMiniDto(
                cardModel.getId(),
                displayName,
                firstImage.getImageUrlSmall(),
                firstImage.getImageUrl(),
                cardModel.getFrameType(),
                restrictionType
            );
        });
    }

    @Transactional
    public void crawlAll() {
        List<CardModel> cards = cardRepository.findAll();

        for (CardModel card : cards) {
            // 이미 한글명이 있고 설명이 채워져 있으면 건너뛰기
            if (card.getKorName() != null && card.getKorDesc() != null) {
                continue;
            }

            String encodedName = encodeCardName(card.getName());
            String primaryUrl = "https://yugioh.fandom.com/wiki/" + encodedName;
            String fallbackUrl = "https://yugipedia.com/wiki/"   + encodedName;

            Document doc = fetchDoc(primaryUrl);
            Document spareDoc = fetchDoc(fallbackUrl);

            // 이름 추출
            String korName = extractKorName(doc, spareDoc);
            if (korName != null) {
                card.setKorName(korName);
            } else {
                log.info("한국어 이름을 찾을 수 없습니다: {}", card.getName());
            }

            // 설명 추출
            boolean isPendulum = PENDULUM_FRAMES.contains(card.getFrameType());
            String korDesc = extractKorDesc(doc, spareDoc, isPendulum);
            if (korDesc != null) {
                card.setKorDesc(korDesc);
            } else {
                log.info("한국어 설명을 찾을 수 없습니다: {}", card.getName());
            }

            cardRepository.save(card);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL 인코딩: 스페이스→언더바, 그 외 안전하게 인코딩
    private String encodeCardName(String name) {
        String tmp = name.replace(" ", "_")
                         .replace("#", "_");
        return URLEncoder.encode(tmp, StandardCharsets.UTF_8);
    }

    // Jsoup 로 문서 가져오기 (실패 시 null 리턴)
    private Document fetchDoc(String url) {
        try {
            return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(10_000)
                        .get();
        } catch (IOException e) {
            log.warn("문서 가져오기 실패 URL={}", url, e);
            return null;
        }
    }

    // 한글 이름 추출: 우선 doc, 없으면 spareDoc
    private String extractKorName(Document doc, Document spareDoc) {
        if (doc != null) {
            Element e = doc.selectFirst("td.cardtablerowdata > span[lang=ko]");
            if (e != null) {
                return e.text();
            }
        }
        if (spareDoc != null) {
            Element e = spareDoc.selectFirst(
                "table.wikitable th:containsOwn(Korean) + td > span[lang=ko]"
            );
            if (e != null) {
                return e.text();
            }
        }
        return null;
    }

    // 한글 설명 추출: pendulum 여부에 따라 각각 처리
    private String extractKorDesc(Document doc, Document spareDoc, boolean pendulum) {
        if (pendulum) {
            // 1) 메인 사이트 시도
            String s = extractPendulumDesc(doc, 
                "td.navbox-list dd > span[lang=ko]");
            if (s != null) {
                return s;
            }
            // 2) 예비 사이트 시도
            return extractPendulumDesc(spareDoc,
                "table.wikitable tr:has(th:containsOwn(Korean)) dl dd > span[lang=ko]");
        } else {
            // 일반 카드
            if (doc != null) {
                Element e = doc.selectFirst("td.navbox-list > span[lang=ko]");
                if (e != null) {
                    return e.text();
                }
            }
            if (spareDoc != null) {
                Element e = spareDoc.selectFirst(
                    "table.wikitable th:containsOwn(Korean) + td + td > span[lang=ko]"
                );
                if (e != null) {
                    return e.text();
                }
            }
            return null;
        }
    }

    // pendulum 전용 설명 처리: 첫 두 dd만 "펜듈럼 효과" / "몬스터 효과" 로 합침
    private String extractPendulumDesc(Document doc, String cssQuery) {
        if (doc == null) return null;

        Elements descs = doc.select(cssQuery);
        if (descs.isEmpty()) return null;

        String[] prefixes = { "펜듈럼 효과: ", "몬스터 효과: " };
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < descs.size() && i < prefixes.length; i++) {
            sb.append(prefixes[i])
              .append("\n")
              .append(descs.get(i).text())
              .append("\n");
        }
        return sb.toString().trim();
    }


    public CardInfoDto getCardInfo(String cardName) {
        String korDesc = "";
        String restrictionType = "unlimited";
        CardModel cardModel;

        if (Pattern.matches("\\d+", cardName)) {
            Long cardId = Long.parseLong(cardName);
            CardImage cardImage = cardImgRepository.findById(cardId).orElseThrow(
                () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다."));
            cardModel = cardImage.getCardModel();
        } else {
            cardModel = cardRepository.findByKorName(cardName)
                                    .orElseGet(() -> {
                                        CardModel byName = cardRepository.findByName(cardName);
                                        if (byName == null) {
                                            throw new IllegalArgumentException("해당 카드가 존재하지 않습니다.");
                                        }
                                        return byName;
                                    });
        }

        String displayName = cardModel.getKorName() != null ?
                cardModel.getKorName() : cardModel.getName();

        if (cardModel.getKorDesc() == null) {
            korDesc = cardModel.getDesc();
        } else {
            korDesc = cardModel.getKorDesc();
        }
        String enRace = cardModel.getRace();
        RaceEnum korRace = RaceEnum.fromEnglishName(enRace);
        LimitRegulation limitRegulation = limitRegulationRepository.findByCardName(cardModel.getName());
        if (limitRegulation == null && cardModel.getKorName() != null) {
            limitRegulation = limitRegulationRepository.findByCardName(cardModel.getKorName());
        }
        if (limitRegulation != null) {
            restrictionType = limitRegulation.getRestrictionType();
        }
        return new CardInfoDto(displayName, korDesc, korRace.getRace(), restrictionType);
    }

    // 리미티드 레귤레이션 크롤링
    public void limitCrawl() {
        limitRegulationRepository.deleteAll();
        try {
             // 웹 페이지 열기
            driver.get("https://ygoprodeck.com/banlist/");

            Thread.sleep(5000);

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
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("forbidden")));

            scrapeListData("forbidden");
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
                    log.info("{} 리스트 : {}", listId, strong.getText());
                    LimitRegulation limitRegulation = new LimitRegulation();
                    limitRegulation.setCardName(strong.getText());
                    limitRegulation.setRestrictionType(listId);
                    limitRegulationRepository.save(limitRegulation);
                }
            }
        } catch (Exception e) {
            log.error("리스트 " + listId + " 데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
        }
    }
}
