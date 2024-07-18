package com.card.Yugioh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.card.Yugioh.model.CardModel;
import java.util.List;


public interface CardRepository extends JpaRepository<CardModel, Long> {
    @Query("SELECT c FROM CardModel c WHERE LOWER(c.name) LIKE LOWER(CONCAT ('%', :query, '%'))")
    List<CardModel> searchByNameContaining(@Param("query")String query);
}
