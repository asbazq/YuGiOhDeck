package com.card.Yugioh.security;

/** WebSocket 클라이언트가 PING을 전송했음을 알리는 도메인 이벤트 */
public record UserPingEvent(String group, String qid, String userId) {}