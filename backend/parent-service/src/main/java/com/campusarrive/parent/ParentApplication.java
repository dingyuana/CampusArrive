package com.campusarrive.parent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 家长查看端服务入口。
 *
 * <p>规格来源：FR-03-01~07（家长查看端）。
 * 职责：手机号验证码绑定、JWT 签发与吊销、报到进度脱敏展示、签到消息推送。</p>
 */
@SpringBootApplication
public class ParentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParentApplication.class, args);
    }
}
