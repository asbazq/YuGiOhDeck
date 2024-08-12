package com.card.Yugioh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class LimitRegulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cardName;

    @Column(nullable = false)
    private String restrictionType;  // 'Ban', 'Limit', 'SemiLimit'

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public void setRestrictionType(String restrictionType) {
        this.restrictionType = restrictionType;
    }

}
    