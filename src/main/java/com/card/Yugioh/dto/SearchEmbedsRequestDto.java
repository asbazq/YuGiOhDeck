package com.card.Yugioh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchEmbedsRequestDto {
    private List<List<Double>> embeds;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("model_version")
    private String modelVersion;
}
