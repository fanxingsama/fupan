package com.meirifupan.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * 每日复盘后端应用入口 —— Spring Boot 启动类。
 * <p>
 * 启动后会在 8080 端口提供 REST API，
 * 前端通过 Vite 代理访问 /api/recaps 接口与后端通信。
 */
public class DailyRecapApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyRecapApplication.class, args);
    }
}
