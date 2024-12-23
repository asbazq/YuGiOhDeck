### YuGiOhDeck

---

![image](https://github.com/user-attachments/assets/ba7d31e0-cf7e-48d4-a630-731af539ca94)


- **프로젝트 주제:** 유희왕 덱 구성
- **개요:** 기존 덱 공유 방식은 영어와 스크린샷 위주로 카드 정보를 제공하여 사용자가 카드 정보를 알아보기 어려움
- **기간:** 2024년 7월 20일 - 2024년 7월 24일
- **기술 스택:** Spring Boot, MySQL, S3, javaScript
- **구현 사항**
    - 유희왕 카드 이미지를 DB에 저장하고 각 카드 ID로 라벨링
        - 영어 및 한글로 검색 시 해당 카드 표출
    - 덱 작성 및 관리
        - 표출된 카드 좌클릭 시 덱에 추가, 우클릭 시 삭제
    - URL에 덱 정보를 저장하여 손쉽게 덱 공유
    - 카드 클릭 시 확대, 빛 반사 이펙트 및 이름 표시
    - 리셋 버튼을 통한 빠른 초기화

### 과정

---

- `mousemove` 이벤트를 통해 마우스 움직임에 따라 카드가 회전, `perspective` 로 3D느낌을 살림
- `radial-gradient` 을 통해 빛 반사 구현

```jsx
overlay.style.background = `radial-gradient(circle at ${bgPosX}% ${bgPosY}%, rgba(255, 255, 255, 0.8), transparent 70%)`;
cardContainer.style.transform = `perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
```

- 덱 정보를 URL에 저장
    - url 글자수 제한이 크롬(8,192자)**,** Internet Explorer(2,083자) 로 많은 양의 데이터를 저장할 수 있지만 최대 75개의 이미지를 저장하는데 한계가 있어, `pako` 를 통해 압축
- `btoa`는 압축된 데이터를 **Base64** 인코딩하여 URL에 포함시킴
- `window.history.pushState`는 이 인코딩된 데이터를 URL의 쿼리 파라미터로 추가하여 현재 페이지의 상태를 URL에 저장
    - 해당 상태의 URL을 공유하는 것 만으로도 간단하게 덱을 공유

```jsx
// url에 저장된 카드 정보를 업데이트
let cardsContent = document.getElementById('cardsContainer').innerHTML;
let extraDeckContent = document.getElementById('extraDeck').innerHTML;

// 두 컨테이너의 내용을 결합
const dataObj = { cardsContent, extraDeckContent };
const dataStr = JSON.stringify(dataObj);
// 데이터 압축
let compressed = pako.deflate(dataStr, { to: 'string' });
let save = btoa(compressed);
// 현재 세션의 상태를 url에 저장
window.history.pushState({data: save}, '', '?deck=' + encodeURIComponent(save));
```

- 최근 일주일 간 추가된 카드가 있다면 정보 크롤링
    - 새로운 카드가 주기적으로 추가되므로 이를 스케줄링하여 크롤링
    - `Jsoup`을 사용하여 카드 정보를 한국어로 업데이트
    - 약 13,000장의 카드 정보를 크롤링하여 데이터베이스를 갱신
        - 다량의 정보와 자동화가 굳이 필요하지 않은 단순 작업이기에 셀리니움보다는 Jsoup을 사용

```jsx
LocalDateTime oneWeekAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
List<CardModel> cardModels = cardRepository.findByCreatedAtAfter(oneWeekAgo);

for (CardModel cardModel : cardModels) {
    String cardName = cardModel.getName();

    // 카드 이름을 URL에 맞게 인코딩
    String encodedUrl = URLEncoder.encode(cardName, StandardCharsets.UTF_8.toString());
    String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl;

    Document doc = Jsoup.connect(completeUrl).get();
    Element korName = doc.selectFirst("td.cardtablerowdata > span[lang=ko]");

    if (korName != null) {
        // 한국어 이름을 찾은 경우
        cardModel.setKorName(korName.text());
        log.info("이름 : {}", korName.text());
    } else {
        // 한국어 이름을 찾지 못한 경우
        cardModel.setKorName(cardModel.getName());
        log.info("한국어 이름을 찾을 수 없습니다.");
    }

    cardRepository.save(cardModel);
```

- 금지 리스트 크롤링
    - 자동화가 필요한 부분은 `Selenium`을 사용하여 크롤링
    - 데이터를 가져오는 시간을 확보하기 위해 `Thread.sleep()` 및 `WebDriverWait` 사용

```jsx
public void limitCrawl() {
        try {
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
            WebElement listElement = driver.findElement(By.id(listId));
            List<WebElement> spans = listElement.findElements(By.xpath(".//span"));
            for (WebElement span : spans) {
                List<WebElement> strongElements = span.findElements(By.xpath(".//span[1]/span[1]/a/strong"));
                for (WebElement strong : strongElements) {
                    log.info("{} 리스트 : {}", listId, strong.getText());
                    LimitRegulation limitRegulation = new LimitRegulation();
                    limitRegulation.setCardName(strong.getText());
                    limitRegulation.setRestrictionType("listId");
                    limitRegulationRepository.save(limitRegulation);
                }
            }

        } catch (Exception e) {
            log.error("데이터를 가져오는 중 오류가 발생했습니다.", e);
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
```

- 외부 API를 통해 카드 이미지 데이터를 수집하고 저장
    - 로컬 서버를 통해 이미지 로딩 속도를 최적화하고 운영 비용을 절감
    - REST API를 구현하여 저장된 이미지를 제공, 데이터 제어 강화

```jsx
public void fetchAndSaveCardImages() throws IOException {
    String response = Request.get(apiUrl).execute().returnContent().asString();
    JSONArray cardData = new JSONObject(response).getJSONArray("data");
    
    for (int i = 0; i < cardData.length(); i++) {
        JSONObject card = cardData.getJSONObject(i);
        JSONArray cardImages = card.getJSONArray("card_images");

        for (int j = 0; j < cardImages.length(); j++) {
            JSONObject imageInfo = cardImages.getJSONObject(j);
            String imageUrl = imageInfo.getString("image_url");
            Long imageId = imageInfo.getLong("id");
            Path outputFile = savePath.resolve(imageId + ".jpg");

             try (InputStream in = new URL(imageUrl).openStream()) {
		            BufferedImage image = ImageIO.read(in);
		            Files.createDirectories(output.getParent());
		            ImageIO.write(image, "jpg", output.toFile());
		        }
        }
    }
}

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
    } catch (MalformedURLException e) {
        return ResponseEntity.badRequest().build();
    }
}
```

- **Google Analytics를 통한 모니터링 및 분석**
    - 사용자 트래픽, 행동 패턴 및 덱 구성 빈도를 분석
    - 분석 결과를 바탕으로 UX/UI 개선 및 기능 최적화
    - 사용자 행동 데이터를 분석하여 가장 많이 사용된 기능을 파악하고 개선 사항 도출

### 디자인

---
![image](https://github.com/user-attachments/assets/fe24651f-63b5-46f9-a4cb-52bfbc2a78fe)

