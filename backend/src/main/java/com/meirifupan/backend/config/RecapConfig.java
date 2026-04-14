package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 复盘配置类 —— 启用 {@link RecapProperties} 配置属性绑定。
 * <p>
 * Spring Boot 在启动时会自动把 application.yml 中 recap.* 前缀的配置项
 * 注入到 RecapProperties record 中，供其他 Service / Provider 通过构造函数注入使用。
 */
@Configuration
@EnableConfigurationProperties(RecapProperties.class)
public class RecapConfig {
}
