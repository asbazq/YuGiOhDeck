package com.card.Yugioh.model;


import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
  name = "limit_regulation_change",
  indexes = {
    @Index(name = "idx_lrc_cardname",   columnList = "cardName")
  }
)
public class LimitRegulationChange {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;                       // PK (auto-increment)

  @Column(nullable = false, length = 255)
  private String cardName;               // 카드 이름 (영문/한글 어떤 기준이든 통일)

  @Column(nullable = false, length = 20)
  private String oldType;                // "unlimited" | "forbidden" | "limited" | "semilimited"

  @Column(nullable = false, length = 20)
  private String newType;                // 위와 동일한 세트

  @ManyToOne(optional = false)
  @JoinColumn(name = "batch_id")
  private LimitRegulationChangeBatch batch;  // 최신 공지 묶음을 구분하기 위한 배치
}
