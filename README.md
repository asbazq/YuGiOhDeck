### YuGiOhDeck

---

**유희왕 덱 구성 및 공유 플랫폼**

---

## 목차

1. [개요](#개요)
2. [기간](#기간)
3. [기술 스택](#기술-스택)
4. [주요 기능](#주요-기능)
5. [설치 및 실행](#설치-및-실행)
6. [URL 덱 공유](#url-덱-공유)
7. [검색 및 Full-Text](#검색-및-full-text)
8. [크롤링 및 스케줄링](#크롤링-및-스케줄링)
9. [금지/제한 리스트 크롤링](#금지제한-리스트-크롤링)
10. [이미지 처리 및 API](#이미지-처리-및-api)
11. [트러블 슈팅](#트러블-슈팅)
12. [대기열 처리](#대기열-처리)
13. [장기간 미사용 사용자 처리](#장기간-미사용-사용자-처리)
14. [모니터링 및 분석](#모니터링-및-분석)
15. [디자인](#디자인)

---

## 개요

![image](https://github.com/user-attachments/assets/0576c27d-67bb-4c86-b619-5dc7dd1d9979)

* 기존 덱 공유 방식의 한계: 영어·스크린샷 위주로 카드 정보 확인이 어려움
* **목표**: 한글·영어 지원 검색, URL 공유, UX 개선을 통해 덱 구성 경험 최적화

## 기간

* 2024년 7월 20일 – 2024년 7월 24일

## 기술 스택

* **백엔드**: Spring Boot, MySQL
* **프론트엔드**: JavaScript, CSS (Tailwind)
* **스토리지**: 로컬
* **자동화**: Jsoup, Selenium
* **검색**: MySQL Full-Text (ngram parser)

---

## 주요 기능

* 카드 검색 (영어/한글) 및 정렬
* 덱 작성·관리 (좌클릭 추가, 우클릭 삭제)
* URL에 덱 상태를 압축·인코딩하여 공유
* 카드 3D 회전·빛 반사 효과, 클릭 확대
* 덱 초기화(리셋) 및 로딩 대기열 처리

---

## 설치 및 실행

1. 리포지토리 클론

   ```bash
   git clone <repo-url>
   ```
2. 백엔드 실행

   ```bash
   ./gradlew bootRun
   ```
3. 프론트엔드 실행

   ```bash
   cd front/my-app
   npm install && npm start
   ```

---

## URL 덱 공유

```js
const dataObj = { cards: cardsContent, extra: extraDeckContent };
const compressed = pako.deflate(JSON.stringify(dataObj), { to: 'string' });
const encoded = btoa(compressed);
window.history.pushState({}, '', `?deck=${encodeURIComponent(encoded)}`);
```

* `pako`로 압축, `btoa`로 Base64 인코딩
* 크롬(8,192자), IE(2,083자)까지 지원, 최대 \~75장 이미지 공유 가능

---

## 검색 및 Full-Text

* **표준 JPQL** (prefix only):

  ```java
  @Query("""
  SELECT c FROM CardModel c
  WHERE (:frameType = '' OR c.frameType = :frameType)
    AND (LOWER(REPLACE(c.korName,' ','')) LIKE CONCAT(:norm,'%')
      OR LOWER(REPLACE(c.name,' ','')) LIKE CONCAT(:norm,'%'))
  """
  Page<CardModel> searchByNameContaining(...);
  ```
* **ngram Full-Text** (중간 검색 지원):

  ```sql
  ALTER TABLE card_model
    ADD COLUMN name_normalized VARCHAR(255)
      GENERATED ALWAYS AS (LOWER(REPLACE(name,' ',''))) STORED,
    ADD COLUMN kor_name_normalized VARCHAR(255)
      GENERATED ALWAYS AS (LOWER(REPLACE(kor_name,' ',''))) STORED;

  ALTER TABLE card_model
    ADD FULLTEXT INDEX ft_idx_name_norm
      (name_normalized, kor_name_normalized)
      WITH PARSER ngram;
  ```

  ```java
  @Query(value = """
    SELECT * FROM card_model
    WHERE (:frameType = '' OR frame_type = :frameType)
      AND MATCH(name_normalized, kor_name_normalized)
          AGAINST(:query IN BOOLEAN MODE)
    """, nativeQuery = true)
  Page<CardModel> searchByFullText(...);
  ```

---

## 크롤링 및 스케줄링

* **Jsoup**: 일주일 간 추가된 카드 크롤링, 한글명 업데이트

  ```java
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
  ```
* **스케줄 설정**: 매일/주기적으로 크롤링 스케줄러 등록

---

## 금지/제한 리스트 크롤링

* **Selenium**: Master Duel 리스트 추출

  ```java
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

  ```

---

## 이미지 처리 및 API

* 외부 API에서 카드 이미지 수집, 로컬서버 저장
* 로컬 캐시 서버로 속도 최적화
* REST endpoint 제공

  ```java
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
  ```

---

## 대기열 처리

* zset으로 대기열을 구축
* 접속 유저 leave 시 자동으로 승급
* 접속 방치 유저 자동 퇴출
* score 별로 차등을 주어 우선 순위 줌

```java
    private void promoteNextUser(String qid) {
        String runKey  = RUNNING_PREFIX + qid;
        String vipKey  = WAITING_PREFIX + "vip";
        String mainKey = WAITING_PREFIX + "main";
        if (totalRunningSize() >= maxRunning()) return;

        TypedTuple<String> vipTuple  = firstWithScore(vipKey);
        TypedTuple<String> mainTuple = firstWithScore(mainKey);

        if (vipTuple == null && mainTuple == null) return;

        double vipScore  = vipTuple  == null ? Double.MAX_VALUE : vipTuple.getScore() - VIP_PRIORITY_BONUS;
        double mainScore = mainTuple == null ? Double.MAX_VALUE : mainTuple.getScore();

        String uid = "";
        boolean isVip = false;
        if (vipScore <= mainScore) {
            uid = vipTuple.getValue();
            isVip = true;
        } else {
            uid = mainTuple.getValue();
            isVip = false;
        }

        if (isVip) redis.opsForZSet().remove(vipKey, uid);
        else redis.opsForZSet().remove(mainKey, uid);

        redis.opsForZSet().add(runKey, uid, Instant.now().toEpochMilli());
        notifier.sendToUser(uid, "{\"type\":\"ENTER\"}");
    }
```
---


## 장기간 미사용 사용자 처리

* **TTL 설정:** Redis Sorted Set의 각 사용자 엔트리마다 score로 타임스탬프를 저장하고, `EXPIRE`를 걸어 세션 만료 시 자동 삭제
* **비활성 사용자 제어 로직:**

  1. `@Scheduled` 어노테이션을 사용하여 10초 주기로 모든 RUNNING ZSet 점검
  2. 현재 시각 기준으로 TTL (예: 5분) 초과 사용자를 검색 후 제거
  3. 세션 만료 사용자에게 WebSocket으로 `TIMEOUT` 메시지 전송 후 RUNNING ZSet에서 제거

```java
long cutoff = System.currentTimeMillis() - sessionTtlMillis();
Set<String> expired = redis.opsForZSet().rangeByScore(runKey, 0, cutoff);
expired.forEach(uid -> notifier.sendToUser(uid, "{\"type\":\"TIMEOUT\"}"));
```

* **Client 측 heartbeat 처리:**

  * React에서 `useCallback`으로 ping 메시지를 주기적으로 서버에 전송하여 세션 유지

```tsx
const sendPing = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send('PING');
    }
}, []);
```

## 트러블 슈팅

### ❗ 전역 WebDriver + @PostConstruct 초기화 문제

```text
java.lang.NullPointerException: Cannot invoke "org.openqa.selenium.WebDriver.get(String)" because "driver" is null
```

### 📌 원인

* `WebDriver`를 **전역 필드로 선언**하고 `@PostConstruct`에서 초기화했지만,
  이후정: 매번 새로 생성

```java
public WebDriver setup() {
    ChromeOptions options = new ChromeOptions();
    options.setBinary(System.getenv("WEB_DRIVER_CHROME_BIN"));
    options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
    return new ChromeDriver(options);
}

public void runCrawl() {
    WebDriver driver = setup();
    try {
        driver.get("https://example.com");
        ...
    } finally {
        driver.quit();
    }
}
```

> 💡 `WebDriver`는 재사용하지 말고, 작업마다 새로 생성하고 종료하는 구조로 변경하세요.

---


## 모니터링 및 분석

* **Google Analytics**: 사용자 행동 분석
* 덱 구성 빈도, 인기 카드 트렌드 추적
* UI/UX 개선 인사이트 수집

---

## 디자인

![image](https://github.com/user-attachments/assets/3e9044ac-91ff-4cc3-88af-866ebcaf6e65)
![image](https://github.com/user-attachments/assets/ef038b14-4ceb-48d4-a327-566e271f3a57)
![image](https://github.com/user-attachments/assets/215f2a22-de91-4a2e-9510-02e3afe12153)
![image](https://github.com/user-attachments/assets/d4f7f146-a847-4bde-83d2-5ff053cbffad)
![image](https://github.com/user-attachments/assets/c7dd0bf7-3d64-4841-9939-ffe19d4d65f7)





---

