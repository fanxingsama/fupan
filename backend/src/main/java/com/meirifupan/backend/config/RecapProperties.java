package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 复盘系统配置属性 —— 映射 application.yml 中 recap.* 前缀的所有配置项。
 * <p>
 * 包含存储目录、数据源选择、Python 解释器路径、采集脚本路径和采集节奏控制等参数。
 * 修改配置请编辑 backend/src/main/resources/application.yml。
 */
@ConfigurationProperties(prefix = "recap")
public record RecapProperties(
        // 复盘 json 的落盘目录。
        String storageRoot,
        // 当前启用的数据源名称，例如 akshare / mock。
        String provider,
        // 执行 Python 采集脚本时使用的解释器路径。
        String pythonExecutable,
        // Python 采集脚本路径。
        String collectorScript,
        // 连续请求上游接口时的间隔秒数，用来控制采集节奏。
        double sleepSeconds
) {
}
