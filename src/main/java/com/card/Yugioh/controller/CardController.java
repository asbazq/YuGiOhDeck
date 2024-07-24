package com.card.Yugioh.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.service.CardService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;

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

    // @GetMapping("/crawl")
    // @ResponseBody
    // public String crawl(@RequestParam String cardName) {
    //     return cardService.crawl(cardName);
    // }

    @GetMapping("/cardinfo")
    @ResponseBody
    public String getCardInfo(@RequestParam String cardName) {
        return cardService.getCardInfo(cardName);
    }
}
