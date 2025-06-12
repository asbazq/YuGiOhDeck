package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class LimitRegulationDto {
    private String name;
    private String imageUrl;
    private String restrictionType;

    public LimitRegulationDto(String name, String imageUrl, String restrictionType) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.restrictionType = restrictionType;
    }
}