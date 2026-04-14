package com.meirifupan.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AKShare 数据提供者 —— 通过 Python 子进程调用 AKShare 采集脟本来获取真实市场数据。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>Java 层通过 ProcessBuilder 拉起 Python 采集脚本（scripts/collect_akshare.py）</li>
 *   <li>Python 脚本通过 AKShare 库访问东方财富等接口，拉取涨停、跌停、板块等数据</li>
 *   <li>Python 将采集结果以 JSON 格式输出到 stdout</li>
 *   <li>Java 层解析 stdout 得到完整的 DailyRecapReport</li>
 * </ol>
 * 采集过程中会自动移除代理环境变量，避免上游接口被本机代理影响。
 */
@Component
public class AkshareMarketRecapProvider implements MarketRecapProvider {

    private final RecapProperties properties;
    private final ObjectMapper objectMapper;

    public AkshareMarketRecapProvider(RecapProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "akshare";
    }

    /**
     * Java 层负责拉起 Python 采集脚本并解析 stdout 返回的完整复盘 JSON。
     */
    @Override
    public DailyRecapReport capture(LocalDate tradeDate) {
        Path scriptPath = Path.of(properties.collectorScript()).toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Collector script not found: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(properties.pythonExecutable());
        command.add("-X");
        command.add("utf8");
        command.add(scriptPath.toString());
        command.add("--date");
        command.add(tradeDate.toString());
        command.add("--sleep");
        command.add(String.valueOf(properties.sleepSeconds()));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptPath.getParent().toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
        // 某些上游网页接口会受本机代理影响，这里统一移除代理变量后再采集。
        processBuilder.environment().remove("HTTP_PROXY");
        processBuilder.environment().remove("HTTPS_PROXY");
        processBuilder.environment().remove("ALL_PROXY");
        processBuilder.environment().remove("http_proxy");
        processBuilder.environment().remove("https_proxy");
        processBuilder.environment().remove("all_proxy");
        processBuilder.environment().put("NO_PROXY", "*");
        processBuilder.environment().put("no_proxy", "*");

        try {
            Process process = processBuilder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Collector failed: " + stderr);
            }
            return objectMapper.readValue(stdout, DailyRecapReport.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute collector script.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Collector execution interrupted.", e);
        }
    }
}
