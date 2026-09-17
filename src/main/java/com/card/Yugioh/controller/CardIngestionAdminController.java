package com.card.Yugioh.controller;

import com.card.Yugioh.model.KoreanReleaseStatus;
import com.card.Yugioh.model.TranslationStatus;
import com.card.Yugioh.repository.CardRepository;
import com.card.Yugioh.service.CardPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/queue/cards")
@RequiredArgsConstructor
public class CardIngestionAdminController {
    private final CardRepository cards;
    private final CardPersistenceService persistence;

    @GetMapping("/translation-pending")
    public Page<PendingCard> pending(Pageable pageable) {
        return cards.findTranslationPending(pageable).map(c -> new PendingCard(c.getId(), c.getName(),
            c.getKorName(), c.getTranslationStatus(), c.getKoreanReleaseStatus()));
    }

    @PatchMapping("/{id}/korean-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable Long id, @RequestParam KoreanReleaseStatus status) {
        if (!cards.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        persistence.setKoreanReleaseStatus(id, status);
    }

    public record PendingCard(Long id, String name, String korName,
                              TranslationStatus translationStatus, KoreanReleaseStatus koreanReleaseStatus) {}
}
