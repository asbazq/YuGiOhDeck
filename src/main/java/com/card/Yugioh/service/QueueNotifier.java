package com.card.Yugioh.service;

/**
 * QueueService 가 GUI(WS)와 직접 엮이지 않도록 중간 계층을 추상화
 */
public interface QueueNotifier {

    /** 전체 사용자에게 JSON 문자열 브로드캐스트 */
    void broadcast(String msg);

    /** 특정 사용자(userId)에게만 JSON 문자열 전송 */
    void sendToUser(String userId, String msg);
}
