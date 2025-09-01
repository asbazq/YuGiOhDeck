package com.card.Yugioh.dto;

import java.util.List;
import lombok.Data;

@Data
public class PredictResponseDto {
    private List<PredictResultDto> detections;
    private Double elapsed;
}