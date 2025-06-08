package com.card.Yugioh.service;

import org.springframework.stereotype.Service;

import com.card.Yugioh.dto.CardMiniDto;
import com.card.Yugioh.model.CardImage;
import com.card.Yugioh.model.CardModel;
import com.card.Yugioh.repository.CardImgRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.support.PageableExecutionUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ElasticCardService {
    private final ElasticsearchOperations operations;
    private final CardImgRepository cardImgRepository;

        public Page<CardMiniDto> search(String keyWord, Pageable pageable) {
        String noSpace = keyWord.replace(" ", "");

        List<String> terms = new ArrayList<>();
        terms.add(noSpace);
        if ("야끼맨".equals(noSpace)) {
            terms.add("크샤트리라펜리르");
        } else if ("크샤트리라펜리르".equals(noSpace)) {
            terms.add("야끼맨");
        }

        BoolQuery.Builder boolBuilder = QueryBuilders.bool();
        for (String term : terms) {
            boolBuilder.should(s -> s.multiMatch(mm -> mm
                .query(term)
                .fields("name^2", "korName^2", "desc", "korDesc")
                .type(TextQueryType.BoolPrefix)
                .fuzziness("AUTO")
            ));
        }

        NativeQuery query = new NativeQueryBuilder()
            .withQuery(boolBuilder.build()._toQuery())
            .withPageable(pageable)
            .build();

        SearchHits<CardModel> hits = operations.search(query, CardModel.class);
        List<CardModel> content = hits.stream().map(SearchHit::getContent).toList();

        return PageableExecutionUtils.getPage(
            content.stream().map(this::toMiniDto).toList(),
            pageable,
            hits::getTotalHits
        );
    }

    private CardMiniDto toMiniDto(CardModel cardModel) {
        List<CardImage> cardImages = cardImgRepository.findByCardModel(cardModel);
        if (cardImages.isEmpty()) {
            return new CardMiniDto(
                cardModel.getId(),
                cardModel.getKorName(),
                "",
                "",
                cardModel.getFrameType()
            );
        }
        CardImage firstImage = cardImages.get(0);
        return new CardMiniDto(
            cardModel.getId(),
            cardModel.getKorName(),
            firstImage.getImageUrlSmall(),
            firstImage.getImageUrl(),
            cardModel.getFrameType()
        );
    }
}