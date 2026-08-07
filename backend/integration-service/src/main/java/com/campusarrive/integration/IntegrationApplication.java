package com.campusarrive.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统集成中间件服务入口。
 *
 * <p>规格来源：FR-04-01~08（系统集成中间件）、SIM-CA-2026-08。
 * 职责：RabbitMQ 事件链编排（签到→缴费→核验→完成）、
 * Debezium CDC 数据同步、主数据映射、下游系统对接适配。</p>
 */
@SpringBootApplication
public class IntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
