package com.card.Yugioh.model;

import java.time.LocalDateTime;
import java.util.List;

import org.checkerframework.common.aliasing.qual.Unique;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@JsonIgnoreProperties(ignoreUnknown = true) // Jackson에게 알려지지 않은 속성을 무시
@EntityListeners(AuditingEntityListener.class)
public class CardModel {
    @Id
    private Long id;
    @CreatedDate
    private LocalDateTime createdAt;
    @Unique
    private String name;
    @Unique
    private String korName;
    private String type;
    private String frameType;
    @Column(name = "`desc`", length = 2048)
    private String desc;
    @Column(length = 2048)
    private String korDesc;
    private int atk;
    private int def;
    private int level;
    private String race;
    private String attribute;
    private String archetype;
     // Generated Column 매핑
    @Column(insertable = false, updatable = false)
    private String nameNormalized;
    @Column(insertable = false, updatable = false)
    private String korNameNormalized;
    @OneToMany(mappedBy = "cardModel")
    private List<CardImage> cardImages;    
}