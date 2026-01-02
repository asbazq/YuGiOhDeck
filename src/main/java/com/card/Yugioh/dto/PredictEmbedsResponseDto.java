package com.card.Yugioh.dto;

import java.util.List;
import lombok.Data;

@Data
public class PredictEmbedsResponseDto {
    private List<EmbedDetectionDto> detections;
    private Double elapsed;
}
