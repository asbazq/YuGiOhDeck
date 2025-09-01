package com.card.Yugioh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.card.Yugioh.model.LimitRegulationChange;
import com.card.Yugioh.model.LimitRegulationChangeBatch;

public interface LimitRegulationChangeRepository extends JpaRepository<LimitRegulationChange, Long> {
  List<LimitRegulationChange> findByBatchOrderByIdAsc(LimitRegulationChangeBatch batch);
}
