package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class CardInfoDto {
    private String name;
    private String korDesc;
    private String race;

    public CardInfoDto(String name, String korDesc, String race) {
        this.name = name;
        this.korDesc = korDesc;
        this.race = race;
    }
}
