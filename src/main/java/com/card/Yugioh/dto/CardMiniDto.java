package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class CardMiniDto {
    private Long id;
    private String name;
    private String imageUrlSmall;
    private String imageUrl;
    private String frameType;
    private String restrictionType;

    public CardMiniDto(Long id, String name, String imageUrlSmall, String imageUrl, String frameType, String restrictionType) {
        this.id = id;
        this.name = name;
        this.imageUrlSmall = imageUrlSmall;
        this.imageUrl = imageUrl;
        this.frameType = frameType;
        this.restrictionType = restrictionType;
    }
}
