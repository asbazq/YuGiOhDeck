package com.card.Yugioh.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.card.Yugioh.model.LimitRegulation;

public interface LimitRegulationRepository extends JpaRepository<LimitRegulation, Long> {

    LimitRegulation findByCardName(String cardName);

}
