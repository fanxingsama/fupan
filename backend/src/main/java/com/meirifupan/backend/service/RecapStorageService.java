package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.RecapListItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class RecapStorageService {

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public RecapStorageService(RecapProperties properties, ObjectMapper objectMapper) throws IOException {
        this.storageRoot = Path.of(properties.storageRoot()).toAbsolutePath();
        this.objectMapper = objectMapper;
        Files.createDirectories(this.storageRoot);
    }

    public DailyRecapReport save(DailyRecapReport report) {
        Path path = filePath(report.tradeDate());
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, report);
            return report;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save recap report: " + path, e);
        }
    }

    public Optional<DailyRecapReport> findByDate(LocalDate tradeDate) {
        Path path = filePath(tradeDate);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            return Optional.of(objectMapper.readValue(inputStream, DailyRecapReport.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read recap report: " + path, e);
        }
    }

    public List<RecapListItem> list() {
        List<RecapListItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try (InputStream inputStream = Files.newInputStream(path)) {
                            DailyRecapReport report = objectMapper.readValue(inputStream, DailyRecapReport.class);
                            items.add(new RecapListItem(
                                    report.tradeDate(),
                                    report.createdAt(),
                                    report.dataSource(),
                                    report.marketStats().upCount(),
                                    report.marketStats().downCount(),
                                    report.marketStats().firstLimitCount()
                            ));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read recap list item: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan recap storage: " + storageRoot, e);
        }
        items.sort(Comparator.comparing(RecapListItem::tradeDate).reversed());
        return items;
    }

    private Path filePath(LocalDate tradeDate) {
        return storageRoot.resolve(tradeDate + ".json");
    }
}
