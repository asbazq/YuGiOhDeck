package com.card.Yugioh.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@JsonIgnoreProperties(ignoreUnknown = true) // Jackson에게 알려지지 않은 속성을 무시
@NoArgsConstructor
public class CardImage {
    @Id
    private Long id;
    private String imageUrl;
    private String imageUrlSmall;
    private String imageUrlCropped;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cardModel")
    private CardModel cardModel;

    public CardImage(Long id, String imageUrl, String imageUrlSmall, String imageUrlCropped, CardModel cardModel) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.imageUrlSmall = imageUrlSmall;
        this.imageUrlCropped = imageUrlCropped;
        this.cardModel = cardModel;
    }
}
