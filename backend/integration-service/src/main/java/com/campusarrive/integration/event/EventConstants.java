package com.campusarrive.integration.event;

/**
 * RabbitMQ 拓扑常量定义。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.1 节队列/交换机拓扑、第 8.3 节死信队列、第 8.4 节重试策略。
 * 集中定义交换机、队列、路由键、DLX 与重试队列名称，供配置类与消费者引用。</p>
 */
public final class EventConstants {

    private EventConstants() {
    }

    // ——— 交换机 ———
    /** 主 topic 交换机，承载所有学生领域事件 */
    public static final String EXCHANGE_STUDENT_EVENTS = "student.events";

    /** 死信交换机（Dead Letter Exchange） */
    public static final String EXCHANGE_DLX = "student.events.dlx";

    /** 重试 DLX — 可重试异常经此交换机路由到阶梯延迟重试队列 */
    public static final String EXCHANGE_RETRY = "student.events.retry";

    // ——— 业务队列 ———
    /** 宿舍确认队列 — 消费 student.checkin.success */
    public static final String QUEUE_DORM_CHECKIN = "q.dorm.checkin";

    /** 一卡通制卡队列 — 消费 student.checkin.success + student.verified.success */
    public static final String QUEUE_CARD_ISSUE = "q.card.issue";

    /** 家长通知队列 — 消费 student.checkin.success */
    public static final String QUEUE_PARENT_NOTIFY = "q.parent.notify";

    /** 教务学籍激活队列 — 消费 student.verified.success */
    public static final String QUEUE_EDU_ACTIVATE = "q.edu.activate";

    /** 教务教籍注册队列 — 消费 student.checkin.completed */
    public static final String QUEUE_EDU_REGISTER = "q.edu.register";

    /** 宿舍正式分配队列 — 消费 student.payment.completed */
    public static final String QUEUE_DORM_ALLOCATE = "q.dorm.allocate";

    /** 流程标记完成队列 — 消费 student.payment.completed */
    public static final String QUEUE_FLOW_COMPLETE = "q.flow.complete";

    // ——— 死信队列 ———
    /** 全局死信队列 */
    public static final String QUEUE_DLX = "q.dlx";

    // ——— 重试队列（TTL 阶梯退避） ———
    /** 第 1 次重试队列，TTL=10s */
    public static final String QUEUE_RETRY_1 = "q.retry.1";

    /** 第 2 次重试队列，TTL=30s */
    public static final String QUEUE_RETRY_2 = "q.retry.2";

    /** 第 3 次重试队列，TTL=60s */
    public static final String QUEUE_RETRY_3 = "q.retry.3";

    // ——— 重试配置 ———
    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 重试延迟阶梯（秒） */
    public static final int[] RETRY_DELAYS_SECONDS = {10, 30, 60};

    // ——— 消息 TTL ———
    /** 业务队列消息 TTL（24 小时，单位毫秒） */
    public static final long MESSAGE_TTL_MS = 86_400_000L;

    /** 队列最大长度（超过后最早消息被挤入死信） */
    public static final int QUEUE_MAX_LENGTH = 10_000;

    // ——— 重试消息头 ———
    /** 重试计数消息头名称 */
    public static final String HEADER_RETRY_COUNT = "x-retry-count";

    /** 原始路由键消息头名称 */
    public static final String HEADER_ORIGINAL_ROUTING_KEY = "x-original-routing-key";

    /** 原始队列消息头名称 */
    public static final String HEADER_ORIGINAL_QUEUE = "x-original-queue";
}
