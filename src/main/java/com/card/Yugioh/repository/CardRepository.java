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

// "SELECT c 
// FROM CardModel c 
// WHERE LOWER(REPLACE(c.korName, ' ', '')) LIKE LOWER(REPLACE(CONCAT('%', :query, '%'), ' ', '')) 
// OR LOWER(REPLACE(c.name, ' ', '')) LIKE LOWER(REPLACE(CONCAT('%', :query, '%'), ' ', ''))"
/* MATCH … AGAINST의 결과값은 단순 Boolean true/false가 아니라 관련도 점수(실수 값) 를 반환 
    ORDER BY … DESC를 추가하면 이 점수가 큰 순서 = 검색어와 더 잘 맞는 카드 먼저 반환 */ 
    @Query(value = """
            SELECT *
            FROM deck.card_model
            WHERE
            (:frameType = '' OR frame_type = :frameType)
            AND (
                MATCH(name_normalized, kor_name_normalized) AGAINST(:query IN BOOLEAN MODE)
                OR LOWER(name) = LOWER(:raw)
                OR LOWER(name_normalized) = LOWER(REPLACE(:raw, ' ', ''))
                OR LOWER(name) LIKE LOWER(CONCAT(:raw, '%'))
                OR LOWER(name) LIKE LOWER(CONCAT('%', :raw, '%'))
            )
            ORDER BY
                (LOWER(name) = LOWER(:raw)) DESC,
                (LOWER(name_normalized) = LOWER(REPLACE(:raw, ' ', ''))) DESC,
                (LOWER(name) LIKE LOWER(CONCAT(:raw, '%'))) DESC,
                MATCH(name_normalized, kor_name_normalized)
                    AGAINST(:query IN BOOLEAN MODE) DESC
            """,
            nativeQuery = true)
    Page<CardModel> searchByFullText(@Param("query") String query,
                                        @Param("frameType") String frameType,
                                        @Param("raw") String raw,
                                        Pageable pageable);
    Optional<CardModel> findByName(String name);
    Optional<CardModel> findByKorName(String korName);
    
    List<CardModel> findByCreatedAtAfter(LocalDateTime localDateTime);
    boolean existsByName(String name);
    List<CardModel> findAllByKorNameIsNullOrKorDescIsNull();
    List<CardModel> findAllByHasKorNameFalseOrHasKorDescFalse();
    Page<CardModel> findByHasKorNameFalseOrHasKorDescFalse(Pageable pageable);
}
