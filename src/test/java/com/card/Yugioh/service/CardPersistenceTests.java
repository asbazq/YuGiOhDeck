package com.card.Yugioh.service;

import com.card.Yugioh.model.*;
import com.card.Yugioh.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CardPersistenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardPersistenceTests {
    @Autowired CardPersistenceService persistence;
    @Autowired CardRepository cards;
    @Autowired CardImgRepository images;

    @BeforeEach void clear() { images.deleteAll(); cards.deleteAll(); }

    @Test void untranslatedCardsAreStoredAndBecomeVisibleWhenTranslationArrives() throws Exception {
        persistence.ingest("{\"id\":1,\"name\":\"New card\"}");
        CardModel card = cards.findById(1L).orElseThrow();
        assertThat(card.getTranslationStatus()).isEqualTo(TranslationStatus.PENDING);
        assertThat(card.isVisibleInKoreanCatalog()).isFalse();
        assertThat(cards.findTranslationPending()).hasSize(1);
        assertThat(persistence.saveTranslation(1L, null, null)).isEqualTo(TranslationStatus.PENDING);
        assertThat(persistence.saveTranslation(1L, "새 카드", null)).isEqualTo(TranslationStatus.PARTIAL);
        assertThat(cards.findTranslationPending()).hasSize(1);
        assertThat(persistence.saveTranslation(1L, null, "설명")).isEqualTo(TranslationStatus.READY);
        assertThat(cards.findTranslationPending()).isEmpty();
        assertThat(cards.findById(1L).orElseThrow().isVisibleInKoreanCatalog()).isTrue();
    }

    @Test void apiUpdatesPreserveTranslationAndExplicitReleaseDecision() throws Exception {
        persistence.ingest("{\"id\":1,\"name\":\"Temporary name\",\"desc\":\"old\"}");
        persistence.saveTranslation(1L, "한글 이름", "한글 설명");
        persistence.setKoreanReleaseStatus(1L, KoreanReleaseStatus.UNRELEASED);
        persistence.ingest("{\"id\":1,\"name\":\"Final name\",\"desc\":\"new\"}");
        CardModel card = cards.findById(1L).orElseThrow();
        assertThat(card.getName()).isEqualTo("Final name");
        assertThat(card.getDesc()).isEqualTo("new");
        assertThat(card.getKorName()).isEqualTo("한글 이름");
        assertThat(card.getKorDesc()).isEqualTo("한글 설명");
        assertThat(card.getTranslationStatus()).isEqualTo(TranslationStatus.READY);
        assertThat(card.getKoreanReleaseStatus()).isEqualTo(KoreanReleaseStatus.UNRELEASED);
        assertThat(card.isVisibleInKoreanCatalog()).isFalse();
    }

    @Test void malformedImageRollsBackOnlyItsCard() throws Exception {
        persistence.ingest("{\"id\":1,\"name\":\"First\"}");
        assertThatThrownBy(() -> persistence.ingest("{\"id\":2,\"name\":\"Broken\",\"card_images\":[{\"id\":20},{\"id\":-1}]}"))
            .isInstanceOf(java.io.IOException.class);
        persistence.ingest("{\"id\":3,\"name\":\"Last\"}");
        assertThat(cards.existsById(1L)).isTrue();
        assertThat(cards.existsById(2L)).isFalse();
        assertThat(cards.existsById(3L)).isTrue();
        assertThat(images.findAll()).isEmpty();
    }

    @Test void conflictingTranslationDoesNotRollbackOtherCards() throws Exception {
        persistence.ingest("{\"id\":1,\"name\":\"First\"}");
        persistence.ingest("{\"id\":2,\"name\":\"Second\"}");
        persistence.saveTranslation(1L, "동일 이름", "설명");
        assertThatThrownBy(() -> persistence.saveTranslation(2L, "동일 이름", "설명"))
            .isInstanceOf(RuntimeException.class);
        assertThat(cards.findById(1L).orElseThrow().getKorName()).isEqualTo("동일 이름");
        assertThat(cards.findById(2L).orElseThrow().getTranslationStatus()).isEqualTo(TranslationStatus.PENDING);
        persistence.saveTranslation(2L, "다른 이름", "설명");
        assertThat(cards.findById(2L).orElseThrow().getTranslationStatus()).isEqualTo(TranslationStatus.READY);
    }

    @Test void blankFieldsStayPendingEvenWhenLegacyFlagsAreWrong() throws Exception {
        persistence.ingest("{\"id\":1,\"name\":\"First\"}");
        CardModel card = cards.findById(1L).orElseThrow();
        card.setKorName("  "); card.setKorDesc("  ");
        card.setHasKorName(true); card.setHasKorDesc(true);
        cards.saveAndFlush(card);
        assertThat(cards.findTranslationPending()).hasSize(1);
        assertThat(cards.findById(1L).orElseThrow().isHasKorName()).isFalse();
    }
}
