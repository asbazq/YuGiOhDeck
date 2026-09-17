package com.card.Yugioh.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CardServiceCrawlTests {
    private final CardService service = new CardService(null, null, null, null, null, null);

    @Test
    void missingTranslationAndFrameTypeAreNormalPendingResults() throws Exception {
        Connection missing = connection(Jsoup.parse("<html><body>No Korean translation yet</body></html>"));
        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(missing);
            var result = service.crawlCard(new CardService.CrawlTarget(1L, "New Card", null, false, false));
            assertThat(result.korName()).isNull();
            assertThat(result.korDesc()).isNull();
        }
    }

    @Test
    void oneSaveFailureDoesNotStopOtherTranslations() {
        var cards = mock(com.card.Yugioh.repository.CardRepository.class);
        var persistence = mock(CardPersistenceService.class);
        CardService crawler = spy(new CardService(cards, persistence, null, null, null, null));
        var first = new com.card.Yugioh.model.CardModel(); first.setId(1L); first.setName("First");
        var second = new com.card.Yugioh.model.CardModel(); second.setId(2L); second.setName("Second");
        when(cards.findTranslationDue(any(), any())).thenReturn(java.util.List.of(first, second));
        doAnswer(invocation -> {
            CardService.CrawlTarget target = invocation.getArgument(0);
            return new CardService.CrawlResult(target.cardId(), "이름", "설명");
        }).when(crawler).crawlCard(any());
        when(persistence.saveTranslation(1L, "이름", "설명")).thenThrow(new IllegalStateException("conflict"));
        when(persistence.saveTranslation(2L, "이름", "설명"))
            .thenReturn(com.card.Yugioh.model.TranslationStatus.READY);
        crawler.crawlAll();
        verify(persistence).saveTranslation(2L, "이름", "설명");
        verify(cards, never()).saveAll(any());
    }

    @Test
    void successfulPrimaryPageWithMissingFieldsFallsBack() throws Exception {
        var primary = Jsoup.parse("<table><tr><td class='cardtablerowdata'><span lang='ko'>기존 이름</span></td></tr></table>");
        var secondary = Jsoup.parse("<table class='wikitable'><tr><th>Korean</th><td><span lang='ko'>보조 이름</span></td><td><span lang='ko'>보조 설명</span></td></tr></table>");
        Connection first = connection(primary);
        Connection second = connection(secondary);
        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect("https://yugioh.fandom.com/wiki/Test_Card")).thenReturn(first);
            jsoup.when(() -> Jsoup.connect("https://yugipedia.com/wiki/Test_Card")).thenReturn(second);
            var result = service.crawlCard(new CardService.CrawlTarget(1L, "Test Card", "effect", false, false));
            assertThat(result.korName()).isEqualTo("기존 이름");
            assertThat(result.korDesc()).isEqualTo("보조 설명");
            verify(second).execute();
        }
    }

    @Test
    void completePrimaryPageDoesNotFetchSecondary() throws Exception {
        var primary = Jsoup.parse("<table><tr><td class='cardtablerowdata'><span lang='ko'>이름</span></td><td class='navbox-list'><span lang='ko'>설명</span></td></tr></table>");
        Connection first = connection(primary);
        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect("https://yugioh.fandom.com/wiki/Test")).thenReturn(first);
            var result = service.crawlCard(new CardService.CrawlTarget(1L, "Test", "effect", false, false));
            assertThat(result.korName()).isEqualTo("이름");
            assertThat(result.korDesc()).isEqualTo("설명");
            jsoup.verify(() -> Jsoup.connect("https://yugipedia.com/wiki/Test"), never());
        }
    }

    private Connection connection(org.jsoup.nodes.Document document) throws Exception {
        Connection connection = mock(Connection.class);
        Connection.Response response = mock(Connection.Response.class);
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.ignoreHttpErrors(true)).thenReturn(connection);
        when(connection.execute()).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.parse()).thenReturn(document);
        return connection;
    }
}
