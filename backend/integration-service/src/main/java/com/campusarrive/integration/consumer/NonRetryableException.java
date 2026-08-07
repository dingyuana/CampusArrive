package com.campusarrive.integration.consumer;

/**
 * 不可重试异常 — 消费失败且不可重试（数据格式错误、业务校验失败）。
 *
 * <p>规格来源：SIM-CA-2026-08 第 8.2 节消费确认机制。
 * 触发 NACK + requeue=false → 死信队列。</p>
 */
public class NonRetryableException extends RuntimeException {
    public NonRetryableException(String message) {
        super(message);
    }
    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
