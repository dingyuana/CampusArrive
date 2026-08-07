package com.campusarrive.integration.event;

/**
 * 迎新核心事件类型枚举。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.2 节事件命名规范、FR-04-05 / FR-04-06。
 * 事件名采用"领域-动作-结果"三段式格式，全部小写，以点号分隔。
 * 事件名同时作为 RabbitMQ 路由键使用。</p>
 */
public enum EventType {

    /** 学生报到成功 — 触发宿舍确认、一卡通预制卡、家长通知 */
    CHECKIN_SUCCESS("student.checkin.success"),

    /** 缴费完成 — 触发宿舍正式分配、流程标记完成 */
    PAYMENT_COMPLETED("student.payment.completed"),

    /** 身份核验通过 — 触发学籍激活、一卡通账号开通 */
    VERIFIED_SUCCESS("student.verified.success"),

    /** 报到全流程完成 — 触发教籍注册、辅导员通知 */
    CHECKIN_COMPLETED("student.checkin.completed");

    private final String routingKey;

    EventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }

    /**
     * 从路由键解析事件类型。
     *
     * @param routingKey RabbitMQ 路由键
     * @return 对应的事件类型
     * @throws IllegalArgumentException 未知路由键
     */
    public static EventType fromRoutingKey(String routingKey) {
        for (EventType type : values()) {
            if (type.routingKey.equals(routingKey)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的事件路由键: " + routingKey);
    }
}
