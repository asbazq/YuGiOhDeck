package com.card.Yugioh.dto;

import java.util.List;
import lombok.Data;

@Data
public class EmbedDetectionDto {
    private List<Double> bbox;
    private List<Double> embed;
}
