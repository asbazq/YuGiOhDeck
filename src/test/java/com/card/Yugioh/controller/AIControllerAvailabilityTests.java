package com.card.Yugioh.controller;

import com.card.Yugioh.dto.*;
import com.card.Yugioh.service.CardLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIControllerAvailabilityTests {
    @Test void pendingFirstCandidateDoesNotHideNextAvailableCandidate() {
        CardLookupService lookup = mock(CardLookupService.class);
        PredictDto available = new PredictDto(2L, "Available", "번역된 카드", null, null, null, "effect");
        when(lookup.enrich(any())).thenAnswer(i -> i.getArgument(0, PredictCandidateDto.class).getId() == 2L ? available : null);
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("{\"results\":[{\"best\":{\"id\":1},\"topk\":[{\"id\":1},{\"id\":2}]}],\"elapsed\":0.1}").build())).build();
        var response = new AIController(client, lookup).predict(
            new SearchEmbedsRequestDto(List.of(List.of(0.1)), 5, null)).block();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UiPredictResponseDto result = (UiPredictResponseDto) response.getBody();
        assertThat(result.getTop1()).isSameAs(available);
        assertThat(result.getTop4()).isEmpty();
    }
}
