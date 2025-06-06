package com.card.Yugioh.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.card.Yugioh.model.CardModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface CardRepository extends JpaRepository<CardModel, Long> {
    // @Query("SELECT c FROM CardModel c WHERE LOWER(c.name) LIKE LOWER(CONCAT ('%', :query, '%'))")
    // List<CardModel> searchByNameContaining(@Param("query")String query);

    @Query("""
       SELECT c
       FROM   CardModel c
       WHERE  c.korName LIKE CONCAT('%', :query, '%')
          OR  c.name    LIKE CONCAT('%', :query, '%')
       """)
    Page<CardModel> searchByNameContaining(@Param("query") String query, Pageable pageable);

    
    CardModel findByName(String name);
    Optional<CardModel> findByKorName(String korName);
    
    List<CardModel> findByCreatedAtAfter(LocalDateTime localDateTime);
    boolean existsByName(String name);
}
