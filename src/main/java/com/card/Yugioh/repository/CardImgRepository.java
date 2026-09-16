package com.card.Yugioh.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;

import java.util.List;


public interface CardImgRepository extends JpaRepository<CardImage, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT i FROM CardImage i JOIN FETCH i.cardModel ORDER BY i.id")
    List<CardImage> findAllWithCard();
    List<CardImage> findByCardModel(CardModel cardModel);
}
