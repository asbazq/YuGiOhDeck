package com.card.Yugioh.service;

import com.card.Yugioh.repository.CardImgRepository;
import com.card.Yugioh.repository.CardRepository;
import org.apache.hc.client5.http.fluent.Request;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ImageServiceTests {
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"id\":\"invalid\",\"name\":\"Broken\"}",
        "{\"name\":\"Missing id\"}",
        "{\"id\":2,\"name\":\" \"}"
    })
    void invalidRowAbortsBeforeAnyWritesAndReleasesFetchLock(String invalidCard) throws Exception {
        CardRepository cards = mock(CardRepository.class);
        CardImgRepository images = mock(CardImgRepository.class);
        ImageService service = new ImageService(cards, images);
        Request request = mock(Request.class, RETURNS_DEEP_STUBS);
        String response = "{\"data\":[{\"id\":1,\"name\":\"First\"}," + invalidCard + ", {\"id\":3,\"name\":\"Last\"}]}";
        when(request.execute().returnContent().asString()).thenReturn(response);
        try (MockedStatic<Request> requests = mockStatic(Request.class)) {
            requests.when(() -> Request.get("https://example.test/cards")).thenReturn(request);
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> service.fetchAndSaveCardImages("https://example.test/cards"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("index 1");
            }
            verifyNoInteractions(cards, images);
        }
    }
}
