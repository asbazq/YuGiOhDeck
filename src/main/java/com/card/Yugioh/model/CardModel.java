package com.card.Yugioh.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@JsonIgnoreProperties(ignoreUnknown = true) // Jackson에게 알려지지 않은 속성을 무시
public class CardModel {
    @Id
    private Long id;
    private String name;
    private String korName;
    private String type;
    private String frameType;
    @Column(length = 1000)
    private String desc;
    @Column(length = 1000)
    private String korDesc;
    private int atk;
    private int def;
    private int level;
    private String race;
    private String attribute;
    private String archetype;
    @OneToMany(mappedBy = "cardModel")
    private List<CardImage> cardImages;    
}