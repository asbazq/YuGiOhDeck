package com.card.Yugioh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.card.Yugioh.model.LimitRegulationChangeBatch;

public interface LimitRegulationChangeBatchRepository extends JpaRepository<LimitRegulationChangeBatch, Long> {
  Optional<LimitRegulationChangeBatch> findTopByOrderByIdDesc(); // 가장 최신 배치
}