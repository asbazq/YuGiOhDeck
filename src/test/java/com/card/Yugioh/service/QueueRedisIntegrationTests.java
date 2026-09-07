package com.card.Yugioh.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Run against a disposable Redis instance only; see README testing instructions. */
@EnabledIfEnvironmentVariable(named = "YUGIOH_TEST_REDIS_PORT", matches = "[0-9]+")
class QueueRedisIntegrationTests {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private QueueService queue;
    private QueueJobService jobs;

    @BeforeEach
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("YUGIOH_TEST_REDIS_PORT")));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        for (String group : List.of("site", "predict")) {
            redis.delete(List.of("running:{" + group + "}:vip", "running:{" + group + "}:main",
                "waiting:{" + group + "}:vip", "waiting:{" + group + "}:main",
                "config:{" + group + "}", "config:{" + group + "}:", "seq:{" + group + "}"));
        }
        QueueNotifier notifier = mock(QueueNotifier.class);
        queue = new QueueService(redis, notifier);
        jobs = new QueueJobService(redis, notifier);
    }

    @AfterEach
    void tearDown() {
        connection.destroy();
    }

    @Test
    void heartbeatDoesNotRecreateDepartedMembership() {
        queue.enter("site", "main", "departed");
        queue.leave("site", "main", "departed");
        queue.touch("site", "main", "departed");
        assertThat(queue.queuePosition("site", "main", "departed").get("pos")).isEqualTo(-1L);
        assertThat(queue.status("site").running()).isZero();
        redis.opsForZSet().add("running:{site}:main", "active", 1);
        queue.touch("site", "main", "active");
        assertThat(redis.opsForZSet().score("running:{site}:main", "active")).isGreaterThan(1);
    }

    @Test
    void scheduledJobFillsCapacityWithoutExpiredSessions() {
        redis.opsForHash().putAll("config:{site}", Map.of("maxRunning", "1", "sessionTtlMillis", "30000"));
        queue.enter("site", "main", "first");
        queue.enter("site", "main", "second");
        queue.enter("site", "main", "third");
        redis.opsForHash().put("config:{site}", "maxRunning", "2");
        jobs.expire();
        assertThat(queue.status("site").running()).isEqualTo(2);
        assertThat(queue.queuePosition("site", "main", "second").get("pos")).isZero();
        assertThat(queue.queuePosition("site", "main", "third").get("pos")).isPositive();
    }

    @Test
    void promotionUsesSharedVipStreakConfiguration() {
        redis.opsForHash().put("config:{site}", "maxRunning", "1");
        queue.enter("site", "vip", "running");
        queue.enter("site", "vip", "vip-next");
        queue.enter("site", "main", "main-next");
        redis.opsForHash().put("config:{site}", "vip_streak", "3");
        queue.leave("site", "vip", "running");
        assertThat(queue.queuePosition("site", "main", "main-next").get("pos")).isZero();
        assertThat(queue.queuePosition("site", "vip", "vip-next").get("pos")).isPositive();
        assertThat(redis.hasKey("config:{site}:")).isFalse();
    }
}
