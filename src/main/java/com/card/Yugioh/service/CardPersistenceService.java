package com.card.Yugioh.service;

import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.model.TranslationStatus;
import com.card.Yugioh.model.KoreanReleaseStatus;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Objects;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A separate proxy makes every card commit or roll back independently. */
@Service
@RequiredArgsConstructor
public class CardPersistenceService {
    private final CardRepository cards;
    private final CardImgRepository images;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<CardImage> ingest(String json) throws IOException {
        CardModel incoming = mapper.readValue(json, CardModel.class);
        if (incoming.getId() == null || incoming.getId() <= 0 || !hasText(incoming.getName())) {
            throw new IOException("Card id and name are required");
        }
        CardModel card = cards.findByIdForUpdate(incoming.getId()).orElseGet(() -> {
            CardModel created = new CardModel();
            created.setId(incoming.getId());
            return created;
        });
        // Set only changed source fields. Hibernate dirty checking skips unchanged rows.
        boolean sourceChanged = !sameSource(card, incoming);
        if (sourceChanged) {
            card.setName(incoming.getName()); card.setType(incoming.getType());
            card.setFrameType(incoming.getFrameType()); card.setDesc(incoming.getDesc());
            card.setAtk(incoming.getAtk()); card.setDef(incoming.getDef()); card.setLevel(incoming.getLevel());
            card.setRace(incoming.getRace()); card.setAttribute(incoming.getAttribute());
            card.setArchetype(incoming.getArchetype());
            card.setTranslationMisses(0);
            card.setNextTranslationCheckAt(null);
            card = cards.saveAndFlush(card);
        }
        JSONArray sourceImages = new JSONObject(json).optJSONArray("card_images");
        List<CardImage> saved = new ArrayList<>();
        if (sourceImages != null) {
            for (int i = 0; i < sourceImages.length(); i++) {
                JSONObject image = sourceImages.getJSONObject(i);
                long id = image.getLong("id");
                if (id <= 0) throw new IOException("Image id must be positive");
                CardImage existing = images.findById(id).orElse(null);
                if (existing != null && !existing.getCardModel().getId().equals(card.getId())) {
                    throw new IOException("Image id belongs to another card: " + id);
                }
                String large = image.optString("image_url", null);
                String small = image.optString("image_url_small", null);
                String cropped = image.optString("image_url_cropped", null);
                if (existing != null && Objects.equals(existing.getImageUrl(), large)
                    && Objects.equals(existing.getImageUrlSmall(), small)
                    && Objects.equals(existing.getImageUrlCropped(), cropped)) saved.add(existing);
                else saved.add(images.save(new CardImage(id, large, small, cropped, card)));
            }
        }
        images.flush();
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TranslationStatus saveTranslation(Long id, String name, String description) {
        CardModel card = cards.findByIdForUpdate(id).orElseThrow();
        TranslationStatus previous = card.getTranslationStatus();
        if (!hasText(card.getKorName()) && hasText(name)) card.setKorName(name.trim());
        if (!hasText(card.getKorDesc()) && hasText(description)) card.setKorDesc(description.trim());
        card.syncTranslationFlags();
        if (card.getTranslationStatus() == TranslationStatus.READY) {
            card.setTranslationMisses(0);
            card.setNextTranslationCheckAt(null);
        } else {
            int misses = card.getTranslationStatus() != previous ? 0
                : Math.min(3, Objects.requireNonNullElse(card.getTranslationMisses(), 0));
            card.setNextTranslationCheckAt(LocalDateTime.now().plusWeeks(1L << misses));
            card.setTranslationMisses(Math.min(3, misses + 1));
        }
        cards.saveAndFlush(card);
        return card.getTranslationStatus();
    }

    @Transactional
    public void setKoreanReleaseStatus(Long id, KoreanReleaseStatus status) {
        CardModel card = cards.findByIdForUpdate(id).orElseThrow();
        card.setKoreanReleaseStatus(status);
        cards.saveAndFlush(card);
    }

    private static boolean sameSource(CardModel a, CardModel b) {
        return Objects.equals(a.getName(), b.getName()) && Objects.equals(a.getType(), b.getType())
            && Objects.equals(a.getFrameType(), b.getFrameType()) && Objects.equals(a.getDesc(), b.getDesc())
            && a.getAtk() == b.getAtk() && a.getDef() == b.getDef() && a.getLevel() == b.getLevel()
            && Objects.equals(a.getRace(), b.getRace()) && Objects.equals(a.getAttribute(), b.getAttribute())
            && Objects.equals(a.getArchetype(), b.getArchetype());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
