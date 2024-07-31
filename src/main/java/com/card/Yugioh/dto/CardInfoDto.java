package com.card.Yugioh.dto;

import lombok.Getter;

@Getter
public class CardInfoDto {
    private String name;
    private String korDesc;

    public CardInfoDto(String name, String korDesc) {
        this.name = name;
        this.korDesc = korDesc;
    }
}
