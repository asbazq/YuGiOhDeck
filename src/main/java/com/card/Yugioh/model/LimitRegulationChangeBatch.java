package com.card.Yugioh.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// LimitRegulationChangeBatch.java
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "limit_regulation_change_batch")
public class LimitRegulationChangeBatch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 오름차순이면 더 “최근” 배치
}
