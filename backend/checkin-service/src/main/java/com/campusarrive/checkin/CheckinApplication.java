package com.campusarrive.checkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 报到流程服务入口。
 *
 * <p>规格来源：FR-00-01~10（v1.0 基线功能保留）。
 * 职责：报到流程指引、校园导航、报到进度追踪、学校自主流程配置、三端协作。</p>
 */
@SpringBootApplication
public class CheckinApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckinApplication.class, args);
    }
}
