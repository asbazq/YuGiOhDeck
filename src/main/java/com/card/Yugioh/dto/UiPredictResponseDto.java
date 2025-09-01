package com.card.Yugioh.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data 
@Builder
public class UiPredictResponseDto {
    private Integer detectedCount;
    private PredictDto top1;
    private List<PredictDto> top4;
    private Double elapsed;
    private String message; // 경고/안내용
}