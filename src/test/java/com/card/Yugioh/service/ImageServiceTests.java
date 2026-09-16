package com.card.Yugioh.service;

import org.apache.hc.client5.http.fluent.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageServiceTests {
    @TempDir Path directory;

    private ImageService service(CardPersistenceService persistence) {
        ImageService service = new ImageService(persistence);
        ReflectionTestUtils.setField(service, "savePath", directory.resolve("large"));
        ReflectionTestUtils.setField(service, "saveSmallPath", directory.resolve("small"));
        return service;
    }

    @Test
    void badCardDoesNotBlockFollowingCardsAndReleasesLock() throws Exception {
        CardPersistenceService persistence = mock(CardPersistenceService.class);
        when(persistence.ingest(anyString())).thenAnswer(invocation -> {
            if (invocation.getArgument(0, String.class).contains("Broken")) throw new IOException("invalid card");
            return List.of();
        });
        ImageService service = service(persistence);
        Request request = mock(Request.class, RETURNS_DEEP_STUBS);
        when(request.execute().returnContent().asString()).thenReturn(
            "{\"data\":[{\"id\":1,\"name\":\"First\"},{\"name\":\"Broken\"},{\"id\":3,\"name\":\"Last\"}]}");
        try (MockedStatic<Request> requests = mockStatic(Request.class)) {
            requests.when(() -> Request.get("https://example.test/cards")).thenReturn(request);
            assertThat(service.fetchAndSaveCardImages("https://example.test/cards")).isEqualTo(2);
            assertThat(service.fetchAndSaveCardImages("https://example.test/cards")).isEqualTo(2);
            verify(persistence, times(6)).ingest(anyString());
        }
    }

    @Test
    void allFailedIsReportedAndCanBeRetried() throws Exception {
        CardPersistenceService persistence = mock(CardPersistenceService.class);
        when(persistence.ingest(anyString())).thenThrow(new IOException("bad data"));
        ImageService service = service(persistence);
        Request request = mock(Request.class, RETURNS_DEEP_STUBS);
        when(request.execute().returnContent().asString()).thenReturn("{\"data\":[{\"id\":1}]}");
        try (MockedStatic<Request> requests = mockStatic(Request.class)) {
            requests.when(() -> Request.get("https://example.test/cards")).thenReturn(request);
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.fetchAndSaveCardImages("https://example.test/cards"))
                    .isInstanceOf(IOException.class).hasMessageContaining("No cards");
            }
        }
    }
}
