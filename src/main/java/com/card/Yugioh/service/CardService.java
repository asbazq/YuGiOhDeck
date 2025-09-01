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

import com.card.Yugioh.dto.BanlistChangeNoticeDto;
import com.card.Yugioh.dto.CardInfoDto;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.dto.LimitRegulationDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.model.LimitRegulation;
import com.card.Yugioh.model.LimitRegulationChange;
import com.card.Yugioh.model.LimitRegulationChangeBatch;
import com.card.Yugioh.model.RaceEnum;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.card.Yugioh.repository.LimitRegulationChangeBatchRepository;
import com.card.Yugioh.repository.LimitRegulationChangeRepository;
import com.card.Yugioh.repository.LimitRegulationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {


    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;
    private final LimitRegulationRepository limitRegulationRepository;
    private final LimitRegulationChangeRepository changeRepo;
    private final LimitRegulationChangeBatchRepository changeBatchRepo;

    // private final AmazonS3 s3Client;
    // private String bucket;

    private static final Set<String> PENDULUM_FRAMES = Set.of(
        "effect_pendulum", "xyz_pendulum",
        "synchro_pendulum", "fusion_pendulum",
        "normal_pendulum"
    );

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    public WebDriver setup() {
        // WebDriverManager를 사용하여 ChromeDriver를 자동으로 관리
        // WebDriverManager.chromedriver().setup();
        // WebDriverManager.chromedriver().browserVersion("127.0.6533.120").setup();
        System.setProperty("selenium.manager.disabled", "true");
        // 컨테이너에 apk로 설치된 크로미움/크롬드라이버 경로
        String chromeBin    = System.getenv("WEB_DRIVER_CHROME_BIN");
        String chromeDriver = System.getenv("WEB_DRIVER_CHROME_DRIVER");

        // 드라이버 시스템 프로퍼티에 경로 지정
        System.setProperty("webdriver.chrome.driver", chromeDriver);

        // 브라우저를 headless 모드로 설정
        ChromeOptions options = new ChromeOptions();
        options.setBinary(chromeBin);
        options.addArguments("--headless", // 브라우저 창을 표시하지 않음
                        "--no-sandbox", 
                        "--disable-dev-shm-usage"
                        ); 

        return new ChromeDriver(options);
    }


    public Page<CardMiniDto> search(String keyWord, String frameType, Pageable pageable) {
        Page<CardModel> cards = cardRepository.searchByFullText(keyWord, frameType == null ? "" : frameType, pageable);
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
        List<CardModel> targets = cardRepository.findAllByHasKorNameFalseOrHasKorDescFalse();

        for (CardModel card : targets) {

            String encodedName = encodeCardName(card.getName());
            String url1 = "https://yugioh.fandom.com/wiki/" + encodedName;
            String url2 = "https://yugipedia.com/wiki/"   + encodedName;

            Document doc = fetchDoc(url1);
            Document spareDoc = (doc == null) ? fetchDoc(url2) : null;

            // 이미 채워져 있지 않은 필드만 채움 (불필요한 재작업 방지)
            if (!card.isHasKorName()) {
                String kn = extractKorName(doc, spareDoc);
                if (kn != null && !kn.isBlank()) {
                    card.setKorName(kn);
                    card.setHasKorName(true); // Generated Column이면 불필요
                }
            }
            if (!card.isHasKorDesc()) {
                boolean isPendulum = PENDULUM_FRAMES.contains(card.getFrameType());
                String kd = extractKorDesc(doc, spareDoc, isPendulum);
                if (kd != null && !kd.isBlank()) {
                    card.setKorDesc(kd);
                    card.setHasKorDesc(true); // Generated Column이면 불필요
                }
            }
        }
        // 루프마다 save() X → 한 번에 flush
        cardRepository.saveAll(targets);
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
        String q = cardName == null ? "" : cardName.trim();
        if (q.isEmpty()) throw new IllegalArgumentException("카드명이 빈 값입니다.");
        String korDesc = "";
        CardModel cardModel;


        // 1) 숫자(이미지 id로 접근)
        if (q.chars().allMatch(Character::isDigit)) {
            Long cardId = Long.parseLong(q);
            CardImage cardImage = cardImgRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 카드가 존재하지 않습니다."));
            cardModel = cardImage.getCardModel();

        // 2) 한글 포함 → kor_name로 1회 조회
        } else if (HANGUL.matcher(q).find()) {
            cardModel = cardRepository.findByKorName(q)
                .orElseThrow(() -> new IllegalArgumentException("해당 카드가 존재하지 않습니다."));

        // 3) 그 외(영문/기타) → name로 1회 조회
        } else {
            cardModel = cardRepository.findByName(q)
                .orElseThrow(() -> new IllegalArgumentException("해당 카드가 존재하지 않습니다."));
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
        LimitRegulation limit = limitRegulationRepository
                .findTopByCardNameIn(List.of(cardModel.getName(), cardModel.getKorName()))
                .orElse(null);

        String restrictionType = (limit != null) ? limit.getRestrictionType() : "unlimited";
        return new CardInfoDto(displayName, korDesc, korRace.getRace(), restrictionType);
    }

    // 리미티드 레귤레이션 크롤링
    @Transactional
    public List<BanlistChangeNoticeDto> limitCrawl() {
        WebDriver driver = setup();
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

            Thread.sleep(5000);

            // 1) 최신 리스트 스냅샷
            Set<String> forbidden   = scrapeListData(driver, "forbidden");
            Set<String> limited     = scrapeListData(driver, "limited");
            Set<String> semilimited = scrapeListData(driver, "semilimited");

            // 충돌 방지: 같은 카드가 여러 리스트에 중복되지 않는다고 가정(사이트가 보장)
            Map<String, String> latest = new HashMap<>();
            forbidden.forEach(n   -> latest.put(n, "forbidden"));
            limited.forEach(n     -> latest.put(n, "limited"));
            semilimited.forEach(n -> latest.put(n, "semilimited"));

            // 2) DB에서 기존 전체 로드 → 빠른 비교용 맵 구성
            List<LimitRegulation> existingAll = limitRegulationRepository.findAll();
            Map<String, LimitRegulation> byName = new HashMap<>(existingAll.size());
            for (LimitRegulation r : existingAll) {
                byName.put(r.getCardName(), r);
            }

            List<BanlistChangeNoticeDto> notices = new ArrayList<>();
            // 3) 업서트(신규/변경만 저장)
            int inserted = 0, updated = 0, unchanged = 0;
            for (Map.Entry<String, String> e : latest.entrySet()) {
                final String cardName = e.getKey();
                final String newType  = e.getValue();

                LimitRegulation cur = byName.get(cardName);
                String oldType = "unlimited";
                if (cur == null) {
                    // 신규
                    LimitRegulation n = new LimitRegulation();
                    n.setCardName(cardName);
                    n.setRestrictionType(newType);
                    limitRegulationRepository.save(n);

                    // 공지 후보 판정 (unlimited → forbidden 만 필터)
                    if (isAnnounceWorthy(oldType, newType)) {
                        notices.add(new BanlistChangeNoticeDto(cardName, oldType, newType));
                    }
                } else {
                    oldType = cur.getRestrictionType().toLowerCase();
                    if (!newType.equals(oldType)) {
                        cur.setRestrictionType(newType);
                        limitRegulationRepository.save(cur);

                        // 공지 후보 판정
                        if (isAnnounceWorthy(oldType, newType)) {
                            notices.add(new BanlistChangeNoticeDto(cardName, oldType, newType));
                        }
                    } 
                }
            }
            // 4) 최신 리스트에 없어진 카드 처리
            Set<String> latestNames = latest.keySet();
            for (LimitRegulation old : existingAll) {
                if (!latestNames.contains(old.getCardName())) {
                    String fromType = old.getRestrictionType().toLowerCase();
                    String toType   = "unlimited";
                    if (!fromType.equals(toType)) {
                        limitRegulationRepository.delete(old);
                        notices.add(new BanlistChangeNoticeDto(old.getCardName(), fromType, toType));
                    }
                }
            }
            
            if (!notices.isEmpty()) {
                LimitRegulationChangeBatch batch = changeBatchRepo.save(LimitRegulationChangeBatch.builder().build());
                for (BanlistChangeNoticeDto n : notices) {
                    LimitRegulationChange row = LimitRegulationChange.builder()
                            .cardName(n.getCardName())
                            .oldType(n.getFromType())
                            .newType(n.getToType())
                            .batch(batch)
                            .build();
                    changeRepo.save(row);
                }
            }

            return notices;
        } catch (Exception e) {
            log.error("데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
            return List.of();
        } finally {
            // 브라우저 닫기
            driver.quit();
        }
    }

    private Set<String> scrapeListData(WebDriver driver, String listId) {
        // list 전체가 로드될 때까지 기다립니다.
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id(listId)));

        try {
            List<WebElement> cards = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#" + listId + " span a strong")));

            // for (WebElement card : cards) {
            //     String name = card.getText();
            //     log.info("{} 리스트 : {}", listId, name);
            //     LimitRegulation limitRegulation = new LimitRegulation();
            //     limitRegulation.setCardName(name);
            //     limitRegulation.setRestrictionType(listId);
            //     limitRegulationRepository.save(limitRegulation);
            // }

            Set<String> names = new HashSet<>();
            for (WebElement card : cards) {
                String name = card.getText().trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception e) {
            log.error("리스트 " + listId + " 데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    private boolean isAnnounceWorthy(String fromType, String toType) {
        return !Objects.equals(
            normalize(fromType),
            normalize(toType)
        );
    }

    private String normalize(String s) {
        return s == null ? "unlimited" : s.trim().toLowerCase();
    }

    @Transactional(readOnly = true)
    public Page<LimitRegulationDto> getLimitRegulations(String type, Pageable pageable) {
        Page<LimitRegulation> limits;
        if (type != null && !type.isEmpty()) {
            limits = limitRegulationRepository.findByRestrictionType(type.toLowerCase(), pageable);
        } else {
            limits = limitRegulationRepository.findAll(pageable);
        }
        return limits.map(limit -> {
            CardModel model = cardRepository.findByKorName(limit.getCardName())
                                          .orElseGet(() -> cardRepository.findByName(limit.getCardName()).orElse(null));
            String name = limit.getCardName();
            String imageUrl = "";
            if (model != null) {
                name = model.getKorName() != null ? model.getKorName() : model.getName();
                List<CardImage> images = cardImgRepository.findByCardModel(model);
                if (!images.isEmpty()) {
                    imageUrl = images.get(0).getImageUrlSmall();
                }
            }
            return new LimitRegulationDto(name, imageUrl, limit.getRestrictionType());
        });
    }
}
