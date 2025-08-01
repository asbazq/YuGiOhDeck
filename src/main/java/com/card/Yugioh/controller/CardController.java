package com.card.Yugioh.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.card.Yugioh.dto.CardInfoDto;
import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.dto.LimitRegulationDto;
import com.card.Yugioh.service.CardService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;

    @GetMapping("/search")
    @ResponseBody
    public Page<CardMiniDto> CardSearch(@RequestParam String keyWord,
                                        @RequestParam(required = false, defaultValue = "") String frameType,
                                        Pageable pageable) {
        return cardService.search(keyWord, frameType, pageable);
    }    

    @GetMapping("/cardinfo")
    @ResponseBody
    public CardInfoDto getCardInfo(@RequestParam String cardName) {
        return cardService.getCardInfo(cardName);
    }
    
    @GetMapping("/limit")
    @ResponseBody
    public Page<LimitRegulationDto> getLimitRegulations(
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return cardService.getLimitRegulations(type, pageable);
    }
}
