package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class CardMiniDto {
    private Long id;
    private String name;
    private String imageUrlSmall;
    private String imageUrl;
    private String frameType;

    public CardMiniDto(Long id, String name, String imageUrlSmall, String imageUrl, String frameType) {
        this.id = id;
        this.name = name;
        this.imageUrlSmall = imageUrlSmall;
        this.imageUrl = imageUrl;
        this.frameType = frameType;
    }
}
