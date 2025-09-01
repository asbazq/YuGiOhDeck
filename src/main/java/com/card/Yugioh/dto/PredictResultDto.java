package com.card.Yugioh.dto;

import java.util.List;

import lombok.Data;

@Data
public class PredictResultDto {
    private PredictCandidateDto best;
    private List<PredictCandidateDto> topk;
}