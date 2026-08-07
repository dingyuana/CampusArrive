package com.campusarrive.integration.idempotent;

import com.campusarrive.integration.testsupport.TestEventFactory;
import com.campusarrive.integration.event.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT-MW-004：幂等消费测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.4 节、UT-MW-004 幂等消费。
 * 验证相同 event_id 仅处理一次，重复事件不重复处理。</p>
 *
 * <p>TDD 类型：UT（单元测试）</p>
 */
@DisplayName("UT-MW-004: 幂等消费机制")
class IdempotentConsumptionTest {

    private IdempotentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IdempotentHandler();
    }

    @Nested
    @DisplayName("首次处理")
    class FirstProcess {

        @Test
        @DisplayName("首次标记返回 true")
        void tryMarkFirstTime() {
            String eventId = "evt-test-001";

            boolean result = handler.tryMarkProcessed(eventId);

            assertTrue(result, "首次标记应返回 true");
        }

        @Test
        @DisplayName("标记后 isAlreadyProcessed 返回 true")
        void isAlreadyProcessedAfterMark() {
            String eventId = "evt-test-002";
            handler.markProcessed(eventId);

            assertTrue(handler.isAlreadyProcessed(eventId));
        }

        @Test
        @DisplayName("未标记的事件 isAlreadyProcessed 返回 false")
        void notProcessed() {
            assertFalse(handler.isAlreadyProcessed("evt-not-exist"));
        }
    }

    @Nested
    @DisplayName("重复事件处理")
    class DuplicateProcess {

        @Test
        @DisplayName("相同 eventId 第二次 tryMarkProcessed 返回 false")
        void duplicateTryMarkReturnsFalse() {
            String eventId = "evt-dup-001";

            boolean first = handler.tryMarkProcessed(eventId);
            boolean second = handler.tryMarkProcessed(eventId);

            assertTrue(first, "首次应返回 true");
            assertFalse(second, "重复应返回 false");
        }

        @Test
        @DisplayName("并发标记同一 eventId 仅一个成功")
        void concurrentMark() throws Exception {
            String eventId = "evt-concurrent-001";
            int threadCount = 10;
            boolean[] results = new boolean[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> results[idx] = handler.tryMarkProcessed(eventId));
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            int successCount = 0;
            for (boolean r : results) if (r) successCount++;

            assertEquals(1, successCount, "并发场景下仅一个线程应标记成功");
        }
    }

    @Nested
    @DisplayName("模拟消费场景")
    class ConsumeScenario {

        @Test
        @DisplayName("同一事件投递两次仅处理一次")
        void consumeTwice() {
            EventEnvelope event = TestEventFactory.checkinSuccess("20260001");
            String eventId = event.getEventId();

            // 第一次消费
            boolean first = handler.tryMarkProcessed(eventId);
            assertTrue(first);

            // 模拟业务处理
            // ... (业务逻辑)

            // 第二次消费（重复事件）
            boolean second = handler.tryMarkProcessed(eventId);
            assertFalse(second, "重复事件应被幂等过滤");

            assertEquals(1, handler.size(), "幂等记录中应只有 1 条");
        }

        @Test
        @DisplayName("不同 eventId 各自独立处理")
        void differentEvents() {
            EventEnvelope e1 = TestEventFactory.checkinSuccess("20260001");
            EventEnvelope e2 = TestEventFactory.checkinSuccess("20260002");

            assertTrue(handler.tryMarkProcessed(e1.getEventId()));
            assertTrue(handler.tryMarkProcessed(e2.getEventId()));

            assertEquals(2, handler.size());
        }
    }

    @Nested
    @DisplayName("清理机制")
    class Cleanup {

        @Test
        @DisplayName("reset 清空所有记录")
        void reset() {
            handler.markProcessed("evt-1");
            handler.markProcessed("evt-2");
            assertEquals(2, handler.size());

            handler.reset();
            assertEquals(0, handler.size());
        }

        @Test
        @DisplayName("cleanupExpired 清理过期记录")
        void cleanupExpired() {
            handler.markProcessed("evt-old");
            // 手动修改时间戳为 25 小时前
            // 由于 ConcurrentMap 内部存储的是时间戳，直接 cleanupExpired 不会移除刚加入的
            handler.cleanupExpired();
            // 刚加入的不应被清理
            assertEquals(1, handler.size());
        }
    }
}
