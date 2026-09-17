package com.card.Yugioh.service;

import com.card.Yugioh.dto.PredictCandidateDto;
import com.card.Yugioh.model.*;
import com.card.Yugioh.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardLookupAvailabilityTests {
    @Test void alternateArtworkUsesParentTranslationAndReleaseStatus() {
        var cards = mock(CardRepository.class);
        var images = mock(CardImgRepository.class);
        var service = new CardLookupService(cards, images);
        var parent = new CardModel(); parent.setId(1L);
        var image = new CardImage(100L, "large", "small", "cropped", parent);
        when(cards.findById(100L)).thenReturn(Optional.empty());
        when(images.findById(100L)).thenReturn(Optional.of(image));
        var candidate = new PredictCandidateDto(); candidate.setId(100L);
        assertThat(service.enrich(candidate)).isNull();
        parent.setKorName("번역된 이름");
        assertThat(service.enrich(candidate).getKorName()).isEqualTo("번역된 이름");
        parent.setKoreanReleaseStatus(KoreanReleaseStatus.UNRELEASED);
        assertThat(service.enrich(candidate)).isNull();
    }

    @Test void directDetailsDoNotExposeUntranslatedCards() {
        var cards = mock(CardRepository.class);
        var card = new CardModel(); card.setId(1L); card.setName("Pending");
        when(cards.findByName("Pending")).thenReturn(Optional.of(card));
        var service = new CardService(cards, null, null, null, null, null);
        assertThatThrownBy(() -> service.getCardInfo("Pending"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
