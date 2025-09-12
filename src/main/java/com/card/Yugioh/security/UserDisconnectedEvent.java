package com.card.Yugioh.security;

/** WebSocket 세션이 끊겼음을 알리는 도메인 이벤트 */
public record UserDisconnectedEvent(String group, String qid, String userId) {}
