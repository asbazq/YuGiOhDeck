package com.card.Yugioh.repository;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.card.Yugioh.model.LimitRegulation;

public interface LimitRegulationRepository extends JpaRepository<LimitRegulation, Long> {

    LimitRegulation findByCardName(String cardName);
    Page<LimitRegulation> findByRestrictionType(String restrictionType, Pageable pageable);
    Page<LimitRegulation> findAll(Pageable pageable);
}
