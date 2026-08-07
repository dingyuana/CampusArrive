package com.campusarrive.integration.consumer;

/**
 * 可重试异常 — 消费失败但可重试（网络抖动、临时不可用）。
 *
 * <p>规格来源：SIM-CA-2026-08 第 8.2 节消费确认机制。
 * 触发 NACK + requeue=false → 重试队列（阶梯退避）。</p>
 */
public class RetryableException extends RuntimeException {
    public RetryableException(String message) {
        super(message);
    }
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
