package com.card.Yugioh.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class LimitRegulationChange {
  @Id 
  @GeneratedValue
  private Long id;
  private String cardName;
  private String oldType;
  private String newType;
  private LocalDateTime changedAt = LocalDateTime.now();
}
