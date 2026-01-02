package com.card.Yugioh.dto;

import java.util.List;
import lombok.Data;

@Data
public class SearchEmbedsResponseDto {
    private List<PredictResultDto> results;
    private Double elapsed;
}
