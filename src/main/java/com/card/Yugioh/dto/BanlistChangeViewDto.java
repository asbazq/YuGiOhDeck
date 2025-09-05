package com.card.Yugioh.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BanlistChangeViewDto {
    private String name;        
    private String cardName;    
    private String fromType;   
    private String toType;      
    private Long imageId;       
    private String thumbUrl;    
}
