package com.card.Yugioh.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.card.Yugioh.dto.CardDataDto;

@RestController 
@RequestMapping("/api/ai")
public class AIController {
    // @PostMapping("/recieve")
    // public ResponseEntity<CardDataDto> receiveCardData(@RequestBody CardDataDto cardDataDto) {
    //     // return cardDataDto(cardDataDto.cardName(), cardDataDto.cardId());
    // }
}
