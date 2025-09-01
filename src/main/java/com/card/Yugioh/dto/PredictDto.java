package com.card.Yugioh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class PredictDto {
    private Long id;

    private String name;

    @JsonProperty("kor_name")
    private String korName;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("image_url_small")
    private String imageUrlSmall;

    @JsonProperty("image_url_cropped")
    private String imageUrlCropped;

    private String frameType;
}