package com.card.Yugioh.service;

import org.jsoup.Connection;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.card.Yugioh.dto.BanlistChangeNoticeDto;
import com.card.Yugioh.dto.BanlistChangeViewDto;
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
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${card.image.save-path}")
    private String savePathString;
    @Value("${card.image.small.save-path}")
    private String saveSmallPathString;


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
        String q = keyWord == null ? "" : keyWord.trim();
        String ftQuery = buildEnglishBooleanQueryIfEnglish(q);
        Page<CardModel> cards = cardRepository.searchByFullText(
                ftQuery,
                frameType == null ? "" : frameType,
                q,
                pageable);
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
                         .replace("#", "_")
                         .replace("<", "")
                         .replace(">", "");
        return URLEncoder.encode(tmp, StandardCharsets.UTF_8);
    }

    // Jsoup 로 문서 가져오기 (실패 시 null 리턴)
    private Document fetchDoc(String url) {
        try {
            Connection.Response resp = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; CardCrawler/1.0)")
                .timeout(10_000)
                .ignoreHttpErrors(true)  // ← 중요: 404도 예외로 던지지 않음
                .execute();

            int sc = resp.statusCode();
            if (sc == 200) {
                return resp.parse();
            }

            // 404/410: 문서 없음 → 조용히 스킵(요약 로그만)
            if (sc == 404 || sc == 410) {
                log.info("문서 없음({}, {})", sc, url);
                return null;
            }

            // 429/5xx: 일시 오류 → 한 번 재시도 (백오프)
            if (sc == 429 || (sc >= 500 && sc < 600)) {
                log.warn("일시 오류로 재시도({}): {}", sc, url);
                Thread.sleep(500L);
                Connection.Response retry = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; CardCrawler/1.0)")
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .execute();
                if (retry.statusCode() == 200) {
                    return retry.parse();
                }
                log.warn("재시도 실패({}, {}): {}", retry.statusCode(), sc, url);
                return null;
            }

            // 기타 코드: 정보 로그만 남기고 스킵
            log.info("문서 가져오기 비정상 상태({}): {}", sc, url);
            return null;

        } catch (IOException e) {
            // 네트워크 예외만 간단 메시지
            log.warn("문서 가져오기 실패(IO): {}", url);
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // Korean 행(tr) 찾기: fandom/yugipedia 공통
    private Element findKoreanRow(Document d) {
        if (d == null) return null;
        return d.selectFirst("table.wikitable tr:has(> th:matchesOwn(^\\s*Korean\\s*$))");
    }

    // 한글 이름 추출: doc → spareDoc 순서
    private String extractKorName(Document doc, Document spareDoc) {
        // fandom 우선 (기존 로직 유지)
        if (doc != null) {
            Element e = doc.selectFirst("td.cardtablerowdata > span[lang=ko]");
            if (e != null) return e.text();
        }
        // yugipedia: tr/td 인덱스 방식 (견고)
        Element tr = findKoreanRow(spareDoc);
        if (tr != null) {
            Elements tds = tr.select("> td");
            if (tds.size() >= 1) {
                Element nameSpan = tds.get(0).selectFirst("span[lang=ko]");
                if (nameSpan != null) return nameSpan.text();
            }
        }
        // 백업: 기존 인접 선택자 (마크업 변형 대비)
        if (spareDoc != null) {
            Element e = spareDoc.selectFirst(
                "table.wikitable th:containsOwn(Korean) + td > span[lang=ko]"
            );
            if (e != null) return e.text();
        }
        return null;
    }

    // 한글 설명 추출: pendulum 여부 분기
    private String extractKorDesc(Document doc, Document spareDoc, boolean pendulum) {
        if (pendulum) {
            // fandom 펜듈럼: dd 2개 우선
            String s = extractPendulumFromFandom(doc);
            if (s != null) return s;

            // yugipedia 펜듈럼: tr/td(두 번째) 내부 dl/dt/dd 파싱
            s = extractPendulumFromYugipedia(spareDoc);
            if (s != null) return s;

            // 백업: 예전 셀렉터
            return extractPendulumDesc(spareDoc,
                "table.wikitable tr:has(th:containsOwn(Korean)) dl dd > span[lang=ko]");
        } else {
            // fandom 일반 설명
            if (doc != null) {
                Element e = doc.selectFirst("td.navbox-list > span[lang=ko]");
                if (e != null) return e.text();
            }
            // yugipedia 일반 설명: tr/td(두 번째) 내부 ko
            Element tr = findKoreanRow(spareDoc);
            if (tr != null) {
                Elements tds = tr.select("> td");
                if (tds.size() >= 2) {
                    Element descSpan = tds.get(1).selectFirst("span[lang=ko]");
                    if (descSpan != null) return descSpan.text();
                }
            }
            // 백업: 기존 인접 선택자
            if (spareDoc != null) {
                Element e = spareDoc.selectFirst(
                    "table.wikitable th:containsOwn(Korean) + td + td > span[lang=ko]"
                );
                if (e != null) return e.text();
            }
            return null;
        }
    }

    // fandom 펜듈럼: dd 두 개 합치기
    private String extractPendulumFromFandom(Document doc) {
        if (doc == null) return null;
        Elements dd = doc.select("td.navbox-list dd > span[lang=ko]");
        if (dd.isEmpty()) return null;
        return joinPendulum(dd);
    }

    // yugipedia 펜듈럼: dl/dt/dd 해석
    private String extractPendulumFromYugipedia(Document d) {
        Element tr = findKoreanRow(d);
        if (tr == null) return null;
        Elements tds = tr.select("> td");
        if (tds.size() < 2) return null;
        Element descCell = tds.get(1);

        // dt/dd 구조 우선
        Element dl = descCell.selectFirst("dl");
        if (dl != null) {
            List<Element> pieces = new ArrayList<>();
            for (Element dt : dl.select("> dt")) {
                Element dd = dt.nextElementSibling();
                if (dd != null && "dd".equals(dd.tagName())) {
                    // dd 안의 ko 텍스트만 수집
                    Element ko = dd.selectFirst("span[lang=ko]");
                    if (ko != null && !ko.text().isBlank()) {
                        // joinPendulum은 Elements를 받으므로 래핑
                        Element wrap = new Element("x").text(ko.text());
                        pieces.add(wrap);
                    }
                }
            }
            if (!pieces.isEmpty()) return joinPendulum(new Elements(pieces));
        }

        // 백업: 셀 전체에서 ko 텍스트 뭉치로 수집
        Elements any = descCell.select("span[lang=ko]");
        if (!any.isEmpty()) return joinPendulum(any);

        // 최후: plain text
        String raw = descCell.text();
        return (raw == null || raw.isBlank()) ? null : raw;
    }

    // 기존 조립 로직 재사용
    private String joinPendulum(Elements pieces) {
        String[] prefixes = {"펜듈럼 효과: ", "몬스터 효과: "};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pieces.size() && i < 2; i++) {
            if (i > 0) sb.append("\n");
            sb.append(prefixes[i]).append("\n").append(pieces.get(i).text());
        }
        return sb.toString().trim();
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
        Page<LimitRegulation> limits = (type != null && !type.isEmpty())
            ? limitRegulationRepository.findByRestrictionType(type.toLowerCase(), pageable)
            : limitRegulationRepository.findAll(pageable);

        return limits.map(limit -> {
            CardModel model = cardRepository.findByKorName(limit.getCardName())
                                          .orElseGet(() -> cardRepository.findByName(limit.getCardName()).orElse(null));
            String name = limit.getCardName();
            String imageUrl = "";
            if (model != null) {
                name = (model.getKorName() != null && !model.getKorName().isBlank())
                    ? model.getKorName()
                    : model.getName();

                List<CardImage> images = cardImgRepository.findByCardModel(model);
                if (!images.isEmpty()) {
                    CardImage img = images.get(0);
                    Long imageId = img.getId();
                    Path localSmall = Paths.get(saveSmallPathString, imageId + ".jpg");
                    Path localLarge = Paths.get(savePathString, imageId + ".jpg");
                    if (Files.exists(localSmall)) {
                        imageUrl = "/images/small/" + imageId + ".jpg";
                    } else if (Files.exists(localLarge)) {
                        imageUrl = "/images/" + imageId + ".jpg";
                    } else if (img.getImageUrlSmall() != null && !img.getImageUrlSmall().isBlank()) {
                        imageUrl = img.getImageUrlSmall();          // 3) 외부 small
                    } else if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                        imageUrl = img.getImageUrl();               // 4) 외부 large
                    } else {
                        imageUrl = ""; // 또는 placeholder 경로를 지정하고 싶다면 여기서 지정
                    }
                }
            }
            return new LimitRegulationDto(name, imageUrl, limit.getRestrictionType());
        });
    }

    @Transactional(readOnly = true)
    public List<BanlistChangeViewDto> getLatestBanlistNotice() {
        // 1) 최신 배치(최근 발표된 스냅샷) 가져오기
        var batchOpt = changeBatchRepo.findTopByOrderByIdDesc(); 
        if (batchOpt.isEmpty()) return List.of();

        var changes = changeRepo.findByBatchOrderByIdAsc(batchOpt.get());
        return changes.stream()
            .map(this::toBanlistChangeView)
            .collect(Collectors.toList());
    }

    // 이름으로 모델/이미지 찾아 한글명 및 썸네일 결정
    private BanlistChangeViewDto toBanlistChangeView(LimitRegulationChange row) {
        final String scrapedName = row.getCardName(); // 크롤러가 가져온 이름

        // 2) kor_name 우선 매칭 → 없으면 영문 name
        var model = cardRepository.findByKorName(scrapedName)
                .orElseGet(() -> cardRepository.findByName(scrapedName).orElse(null));

        String displayName = scrapedName;
        Long imageId = null;
        String thumbUrl = "";

        if (model != null) {
            displayName = (model.getKorName() != null && !model.getKorName().isBlank())
                    ? model.getKorName()
                    : model.getName();

            var images = cardImgRepository.findByCardModel(model);
            if (!images.isEmpty()) {
                var img = images.get(0);
                imageId = img.getId();  // CardImage의 PK가 곧 파일명 id 라고 가정

                Path localSmall = Paths.get(saveSmallPathString, imageId + ".jpg");
                Path localLarge = Paths.get(savePathString, imageId + ".jpg");

                // 3) 썸네일 폴백: 로컬 small → 로컬 large → 외부 small → 외부 large
                if (Files.exists(localSmall)) {
                    thumbUrl = "/images/small/" + imageId + ".jpg";
                } else if (Files.exists(localLarge)) {
                    thumbUrl = "/images/" + imageId + ".jpg";
                } else if (img.getImageUrlSmall() != null && !img.getImageUrlSmall().isBlank()) {
                    thumbUrl = img.getImageUrlSmall();
                } else if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    thumbUrl = img.getImageUrl();
                }
            }
        }

        return BanlistChangeViewDto.builder()
                .name(displayName)           // ✅ 프론트에서는 무조건 c.name 쓰면 한글 우선
                .cardName(scrapedName)
                .fromType(row.getOldType().toLowerCase())
                .toType(row.getNewType().toLowerCase())
                .imageId(imageId)
                .thumbUrl(thumbUrl)
                .build();
    }

    /**
     * Build a MySQL FULLTEXT BOOLEAN MODE query for English input.
     * - Adds an exact phrase ("...") to boost adjacent word matches
     * - Adds +token* for each alphanumeric token to enforce AND + prefix search
     * - If Hangul exists, returns original string to keep Korean behavior unchanged
     */
    private String buildEnglishBooleanQueryIfEnglish(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return s;
        if (HANGUL.matcher(s).find()) return s;

        String[] tokens = s.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        if (tokens.length >= 2) {
            sb.append('"').append(s).append('"').append(' ');
        }
        for (String t : tokens) {
            if (t == null) continue;
            String w = t.trim();
            if (w.isEmpty()) continue;
            sb.append('+').append(w).append('*').append(' ');
        }
        String built = sb.toString().trim();
        return built.isEmpty() ? s : built;
    }
}
