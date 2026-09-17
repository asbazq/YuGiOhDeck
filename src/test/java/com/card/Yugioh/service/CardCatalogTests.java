package com.card.Yugioh.service;

import com.card.Yugioh.model.*;
import com.card.Yugioh.repository.CardImgRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardCatalogTests {
    @TempDir Path directory;
    @Test void exportsAlternateImageIdsAndSkipsQueriesWhenNothingChanged() throws Exception {
        var images = mock(CardImgRepository.class);
        var catalog = new CardCatalogService(images);
        ReflectionTestUtils.setField(catalog, "imageDirectory", directory.toString());
        var card = new CardModel(); card.setId(1L); card.setName("Name, \"quoted\""); card.setType("Effect Monster");
        when(images.findAllWithCard()).thenReturn(List.of(new CardImage(10L, null, null, null, card)));
        catalog.export(false);
        assertThat(Files.readString(directory.resolve("catalog.csv")))
            .isEqualTo("id,card_id,name,type\n10,1,\"Name, \"\"quoted\"\"\",\"Effect Monster\"\n");
        catalog.export(false);
        verify(images, times(1)).findAllWithCard();
    }
}
