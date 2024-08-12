package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class CardInfoDto {
    private String name;
    private String korDesc;
    private String race;
    private String restrictionType;

    public CardInfoDto(String name, String korDesc, String race, String restrictionType) {
        this.name = name;
        this.korDesc = korDesc;
        this.race = race;
        this.restrictionType = restrictionType;
    }
}
