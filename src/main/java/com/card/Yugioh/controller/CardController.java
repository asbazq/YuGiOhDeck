package com.card.Yugioh.controller;

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
import com.card.Yugioh.service.ElasticCardService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;
    private final ElasticCardService elasticCardService;

    @GetMapping("/search")
    @ResponseBody
    public Page<CardMiniDto> CardSearch(@RequestParam String keyWord, Pageable pageable) {
        return cardService.search(keyWord, pageable);
    }    

     @GetMapping("/search-es")
    @ResponseBody
    public Page<CardMiniDto> searchByElasticsearch(@RequestParam String keyWord, Pageable pageable) {
        return elasticCardService.search(keyWord, pageable);
    }

    @GetMapping("/cardinfo")
    @ResponseBody
    public CardInfoDto getCardInfo(@RequestParam String cardName) {
        return cardService.getCardInfo(cardName);
    }
}
