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
public class WencaiMarketRecapProvider implements MarketRecapProvider {

    private final RecapProperties properties;
    private final ObjectMapper objectMapper;

    public WencaiMarketRecapProvider(RecapProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "wencai";
    }

    @Override
    public DailyRecapReport capture(LocalDate tradeDate) {
        if (properties.wencaiCookie() == null || properties.wencaiCookie().isBlank()) {
            throw new IllegalStateException("Missing WENCAI_COOKIE. Set it before calling capture.");
        }

        Path scriptPath = Path.of(properties.collectorScript()).toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Collector script not found: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(properties.pythonExecutable());
        command.add(scriptPath.toString());
        command.add("--date");
        command.add(tradeDate.toString());
        command.add("--cookie");
        command.add(properties.wencaiCookie());
        command.add("--sleep");
        command.add(String.valueOf(properties.sleepSeconds()));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptPath.getParent().toFile());

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
