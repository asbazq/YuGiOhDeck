package com.card.Yugioh.controller;

import java.io.IOException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.service.CardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.regex.Pattern;


@Slf4j
@Controller
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;
    private final CardImgRepository cardImgRepository;

    @GetMapping("/fetchAndSaveImages")
    public String fetchAndSaveCardImages() {
        try {
            cardService.fetchAndSaveCardImages();
            return "Card images fetched and saved successfully!";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch and save card images";
        }
    }

    // @GetMapping("/cardImages")
    // @ResponseBody
    // public List<String> getCardImageUrls(Model model) {
    //     List<String> imageUrls = cardService.getCardImageUrls();
    //     return imageUrls;
    // }

    @GetMapping("/search")
    @ResponseBody
    public List<CardMiniDto> CardSearch(@RequestParam String keyWord) {
        return cardService.search(keyWord);
    }    

    @GetMapping("/crawl")
    @ResponseBody
    public String crawl(@RequestParam String cardName) {
        if (Pattern.matches("\\d+", cardName)) {
            Long cardId = (long) Integer.parseInt(cardName);
            CardImage cardImage = cardImgRepository.findById(cardId).orElseThrow(
                () -> new IllegalArgumentException("해당 카드가 존재하지 않습니다.")
            );
            cardName = cardImage.getCardModel().getName();
        }
        try {
            // String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
            String decodedUrl = URLDecoder.decode(cardName, StandardCharsets.UTF_8.toString());
            String modifiedUrl = decodedUrl.replace(" ", "_");
            String encodedUrl = URLEncoder.encode(modifiedUrl, StandardCharsets.UTF_8.toString())
                                            .replace("%2F", "/")
                                            .replace("%3A", ":")
                                            .replace("%3F", "?")
                                            .replace("%3D", "=");
                                            


            String completeUrl = "https://yugioh.fandom.com/wiki/" + encodedUrl + "?so=search";

            // 웹 페이지의 HTML
            Document doc = Jsoup.connect(completeUrl).get();
            // 카드 정보가 있는 테이블
            Elements rows = doc.select("div.mw-parser-output > table.cardtable > tbody > tr");

            for (Element row : rows) {
                // "Korean" 헤더
                Element header = row.selectFirst("th.cardtablerowheader");
                if (header != null && header.text().equals("Korean")) {
                     // 한국어 이름이 있는 셀
                    Element koreanContent = row.selectFirst("td.cardtablerowdata");
                    if (koreanContent != null) {
                        // 한국어 이름을 반환
                        String originalText = koreanContent.text();
                        String replaceText = originalText.replace(" Check translation", "");  
                        log.info("카드 한국어 이름 : {}", replaceText);
                        return replaceText;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "데이터를 가져오는 중 오류가 발생했습니다.";
        }

        return cardName;
    }
}
