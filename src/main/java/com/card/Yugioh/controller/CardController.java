package com.card.Yugioh.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.card.Yugioh.dto.CardInfoDto;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.service.CardService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;

    // @GetMapping("/cardImages")
    // @ResponseBody
    // public List<String> getCardImageUrls(Model model) {
    //     List<String> imageUrls = cardService.getCardImageUrls();
    //     return imageUrls;
    // }

    @GetMapping("/search")
    @ResponseBody
    public Page<CardMiniDto> CardSearch(@RequestParam String keyWord, Pageable pageable) {
        return cardService.search(keyWord, pageable);
    }    

    // @GetMapping("/crawl")
    // @ResponseBody
    // public String crawl(@RequestParam String cardName) {
    //     return cardService.crawl(cardName);
    // }

    @GetMapping("/cardinfo")
    @ResponseBody
    public CardInfoDto getCardInfo(@RequestParam String cardName) {
        return cardService.getCardInfo(cardName);
    }
}
