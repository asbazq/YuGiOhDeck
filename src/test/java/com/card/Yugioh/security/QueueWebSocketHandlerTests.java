package com.card.Yugioh.security;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueueWebSocketHandlerTests {
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final QueueWebSocketHandler handler = new QueueWebSocketHandler(publisher);

    @ParameterizedTest
    @ValueSource(strings = {"", "group=other&qid=main&userId=u", "group=site&qid=other&userId=u",
        "group=site&qid=main", "group=site&qid=main&userId="})
    void rejectsInvalidQueueIdentityWithoutPublishingEvents(String query) throws Exception {
        WebSocketSession session = session(query);
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("PING"));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(publisher);
    }

    @Test
    void replacedConnectionCannotRefreshOrRemoveCurrentMembership() throws Exception {
        WebSocketSession oldSession = session("group=site&qid=main&userId=u");
        WebSocketSession current = session("group=site&qid=main&userId=u");
        handler.afterConnectionEstablished(oldSession);
        handler.afterConnectionEstablished(current);
        verify(oldSession).close(CloseStatus.NORMAL);
        handler.handleTextMessage(oldSession, new TextMessage("PING"));
        handler.afterConnectionClosed(oldSession, CloseStatus.NORMAL);
        verifyNoInteractions(publisher);
        handler.handleTextMessage(current, new TextMessage("PING"));
        verify(publisher).publishEvent(new UserPingEvent("site", "main", "u"));
        handler.afterConnectionClosed(current, CloseStatus.NORMAL);
        verify(publisher).publishEvent(new UserDisconnectedEvent("site", "main", "u"));
    }

    @Test
    void doesNotSendToClosedSocket() throws Exception {
        WebSocketSession session = session("group=site&qid=main&userId=u");
        handler.afterConnectionEstablished(session);
        when(session.isOpen()).thenReturn(false);
        handler.sendToUser("site", "u", "hello");
        verify(session, never()).sendMessage(any());
    }

    @Test
    void serializesConcurrentNotificationsToOneSocket() throws Exception {
        WebSocketSession session = session("group=site&qid=main&userId=u");
        handler.afterConnectionEstablished(session);
        CountDownLatch firstSending = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        doAnswer(invocation -> {
            if (sends.incrementAndGet() == 1) {
                firstSending.countDown();
                if (!releaseFirst.await(3, TimeUnit.SECONDS)) throw new AssertionError("First send was not released");
            }
            return null;
        }).when(session).sendMessage(any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> handler.sendToUser("site", "u", "first"));
            assertThat(firstSending.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                handler.broadcast("second");
            });
            assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(sends.get()).isEqualTo(2);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private WebSocketSession session(String query) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/queue-status?" + query));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
