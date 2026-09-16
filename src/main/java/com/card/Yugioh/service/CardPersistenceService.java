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
import org.springframework.beans.BeanUtils;
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
        // English API updates must not reset locally collected Korean data/release decisions.
        BeanUtils.copyProperties(incoming, card, "id", "createdAt", "korName", "korDesc",
            "hasKorName", "hasKorDesc", "koreanReleaseStatus", "cardImages",
            "nameNormalized", "korNameNormalized");
        cards.saveAndFlush(card);
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
                saved.add(images.save(new CardImage(id, image.optString("image_url", null),
                    image.optString("image_url_small", null),
                    image.optString("image_url_cropped", null), card)));
            }
        }
        images.flush();
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TranslationStatus saveTranslation(Long id, String name, String description) {
        CardModel card = cards.findByIdForUpdate(id).orElseThrow();
        if (!hasText(card.getKorName()) && hasText(name)) card.setKorName(name.trim());
        if (!hasText(card.getKorDesc()) && hasText(description)) card.setKorDesc(description.trim());
        card.syncTranslationFlags();
        cards.saveAndFlush(card);
        return card.getTranslationStatus();
    }

    @Transactional
    public void setKoreanReleaseStatus(Long id, KoreanReleaseStatus status) {
        CardModel card = cards.findByIdForUpdate(id).orElseThrow();
        card.setKoreanReleaseStatus(status);
        cards.saveAndFlush(card);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
