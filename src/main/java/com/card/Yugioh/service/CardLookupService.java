package com.card.Yugioh.service;

import com.card.Yugioh.dto.PredictCandidateDto;
import com.card.Yugioh.dto.PredictDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardLookupService {

    private final CardRepository cardRepository;
    private final CardImgRepository cardImgRepository;

    @Transactional(readOnly = true)
    public PredictDto enrich(PredictCandidateDto pc) {
        if (pc == null || pc.getId() == null) return null;

        Long id = pc.getId();
        Optional<CardModel> modelOpt = cardRepository.findById(id);
        Optional<CardImage> imgOpt   = cardImgRepository.findById(id);

        String korName = modelOpt.map(CardModel::getKorName).orElse(null);
        String frameType = modelOpt.map(CardModel::getFrameType).orElse(null);

        String imageUrl = null, imageUrlSmall = null, imageUrlCropped = null;
        if (imgOpt.isPresent()) {
            CardImage img = imgOpt.get();
            imageUrl        = img.getImageUrl();
            imageUrlSmall   = img.getImageUrlSmall();
            imageUrlCropped = img.getImageUrlCropped();
        }

        return new PredictDto(
                id,
                pc.getName(),
                korName,
                imageUrl,
                imageUrlSmall,
                imageUrlCropped,
                frameType
        );
    }
}
