package com.campusarrive.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关服务入口。
 *
 * <p>规格来源：SAD 第 4 节网关层设计、FR-04-01~04（路由/鉴权/限流/协议适配）。
 * 职责：统一入口，承担路由分发、JWT 鉴权、令牌桶限流、SOAP↔REST 协议适配。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
