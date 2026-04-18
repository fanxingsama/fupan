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

@Component
public class TushareMarketRecapProvider implements MarketRecapProvider {

    private final RecapProperties properties;
    private final ObjectMapper objectMapper;

    public TushareMarketRecapProvider(RecapProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "tushare";
    }

    @Override
    public DailyRecapReport capture(LocalDate tradeDate) {
        Path scriptPath = Path.of(properties.collectorScript()).toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Tushare 采集脚本不存在: " + scriptPath);
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
                String detail = stderr.isBlank() ? "未返回具体错误。" : stderr.trim();
                throw new IllegalStateException("Tushare 采集失败，请检查 token、代理地址或接口权限。详情: " + detail);
            }
            DailyRecapReport report = objectMapper.readValue(stdout, DailyRecapReport.class);
            if (report.firstLimitToday() == null || report.firstLimitToday().isEmpty()) {
                throw new IllegalStateException("Tushare 未返回有效复盘数据，请检查当日权限或代理接口状态。");
            }
            return report;
        } catch (IOException e) {
            throw new IllegalStateException("无法执行 Tushare 采集脚本。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tushare 采集中断。", e);
        }
    }
}
