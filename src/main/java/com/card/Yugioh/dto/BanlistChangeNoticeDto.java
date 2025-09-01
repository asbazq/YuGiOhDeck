package com.card.Yugioh.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter 
@AllArgsConstructor
public class BanlistChangeNoticeDto {
    private final String cardName;
    private final String fromType; // "forbidden" | "limited" | "semilimited" | "unlimited"
    private final String toType;
}
