package com.meirifupan.backend.service;

import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.TradeImportResponse;
import com.meirifupan.backend.model.TradeJournalDay;
import com.meirifupan.backend.model.TradeJournalDay.MarketContext;
import com.meirifupan.backend.model.TradeRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TradeJournalService {

    private final JdbcTemplate jdbc;
    private final RecapStorageService recapStorageService;
    private final IndicatorService indicatorService;
    private final TradePlanService tradePlanService;

    public TradeJournalService(
            JdbcTemplate jdbc,
            RecapStorageService recapStorageService,
            IndicatorService indicatorService,
            TradePlanService tradePlanService
    ) {
        this.jdbc = jdbc;
        this.recapStorageService = recapStorageService;
        this.indicatorService = indicatorService;
        this.tradePlanService = tradePlanService;
    }

    public TradeImportResponse importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new TradeImportResponse(0, 0, List.of("未选择文件。"));
        }

        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(line -> !line.isBlank()).toList();
            if (lines.isEmpty()) {
                return new TradeImportResponse(0, 0, List.of("导入文件为空。"));
            }

            char delimiter = detectDelimiter(lines.get(0));
            List<String> headers = splitLine(lines.get(0), delimiter);
            Map<String, Integer> headerMap = buildHeaderMap(headers);

            Map<LocalDate, List<TradeRecord>> toSave = new LinkedHashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                List<String> values = splitLine(lines.get(i), delimiter);
                Optional<TradeRecord> parsed = parseTradeRecord(values, headerMap, file.getOriginalFilename(), warnings, i + 1);
                if (parsed.isEmpty()) {
                    skipped++;
                    continue;
                }

                TradeRecord record = parsed.get();
                List<TradeRecord> existing = loadByDate(record.tradeDate());
                boolean duplicate = existing.stream().anyMatch(item -> sameTrade(item, record));
                if (duplicate) {
                    skipped++;
                    continue;
                }

                toSave.computeIfAbsent(record.tradeDate(), ignored -> new ArrayList<>()).add(record);
                imported++;
            }

            for (Map.Entry<LocalDate, List<TradeRecord>> entry : toSave.entrySet()) {
                for (TradeRecord record : entry.getValue()) {
                    saveRecord(record);
                }
            }
        } catch (IOException ex) {
            warnings.add("导入失败：" + ex.getMessage());
        }

        return new TradeImportResponse(imported, skipped, warnings);
    }

    public List<TradeJournalDay> listJournal() {
        List<LocalDate> dates = listDates();
        List<TradeJournalDay> result = new ArrayList<>();
        for (LocalDate date : dates) {
            List<TradeRecord> trades = loadByDate(date);
            if (trades.isEmpty()) {
                continue;
            }
            int buyCount = (int) trades.stream().filter(item -> item.side().contains("买")).count();
            int sellCount = (int) trades.stream().filter(item -> item.side().contains("卖")).count();
            double totalAmount = trades.stream().mapToDouble(TradeRecord::amount).sum();
            result.add(new TradeJournalDay(
                    date,
                    trades.size(),
                    buyCount,
                    sellCount,
                    round2(totalAmount),
                    buildMarketContext(date),
                    trades
            ));
        }
        result.sort(Comparator.comparing(TradeJournalDay::tradeDate).reversed());
        return result;
    }

    private MarketContext buildMarketContext(LocalDate tradeDate) {
        Optional<DailyRecapReport> reportOpt = recapStorageService.findByDate(tradeDate);
        if (reportOpt.isEmpty()) {
            return new MarketContext("无复盘数据", "-", "-", "当日尚未采集复盘数据", "-");
        }
        DailyRecapReport report = reportOpt.get();
        var recent = recapStorageService.loadRecent(tradeDate, 20);
        var indicators = indicatorService.calculate(report, recent);
        var plan = tradePlanService.buildPlan(report, indicators);
        String leadingTheme = plan.primaryThemes().isEmpty() ? "-" : plan.primaryThemes().get(0).name();
        return new MarketContext(
                indicators.emotionLabel(),
                plan.tradeMode(),
                plan.marketBias(),
                plan.headline(),
                leadingTheme
        );
    }

    private Optional<TradeRecord> parseTradeRecord(List<String> values, Map<String, Integer> headerMap, String sourceFile, List<String> warnings, int rowNumber) {
        try {
            LocalDate tradeDate = parseDate(valueOf(values, headerMap, Set.of("tradeDate", "成交日期", "日期", "发生日期")));
            String code = valueOf(values, headerMap, Set.of("code", "证券代码", "股票代码", "代码"));
            String name = valueOf(values, headerMap, Set.of("name", "证券名称", "股票名称", "名称"));
            String side = valueOf(values, headerMap, Set.of("side", "操作", "买卖标志", "买卖", "业务名称"));
            double price = parseDouble(valueOf(values, headerMap, Set.of("price", "成交均价", "成交价格", "价格")));
            int quantity = (int) Math.round(parseDouble(valueOf(values, headerMap, Set.of("quantity", "成交数量", "成交股数", "数量"))));
            double amount = parseDouble(valueOf(values, headerMap, Set.of("amount", "成交金额", "发生金额", "金额")));
            double fee = parseDouble(valueOf(values, headerMap, Set.of("fee", "手续费", "佣金", "印花税", "其他费", "费用")));

            if (tradeDate == null || code.isBlank() || side.isBlank()) {
                warnings.add("第 " + rowNumber + " 行缺少关键字段，已跳过。");
                return Optional.empty();
            }

            return Optional.of(new TradeRecord(
                    UUID.randomUUID().toString(),
                    tradeDate,
                    code,
                    name,
                    side,
                    price,
                    quantity,
                    amount,
                    fee,
                    sourceFile == null ? "unknown" : sourceFile,
                    OffsetDateTime.now()
            ));
        } catch (Exception ex) {
            warnings.add("第 " + rowNumber + " 行解析失败：" + ex.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Integer> buildHeaderMap(List<String> headers) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            map.put(normalizeHeader(headers.get(i)), i);
        }
        return map;
    }

    private String valueOf(List<String> values, Map<String, Integer> headerMap, Set<String> aliases) {
        for (String alias : aliases) {
            Integer index = headerMap.get(normalizeHeader(alias));
            if (index != null && index < values.size()) {
                return values.get(index).trim();
            }
        }
        return "";
    }

    private List<TradeRecord> loadByDate(LocalDate date) {
        return jdbc.query(
                "SELECT id, trade_date, code, name, side, price, quantity, amount, fee, source_file, imported_at FROM trade_record WHERE trade_date = ? ORDER BY imported_at",
                (rs, rowNum) -> new TradeRecord(
                        rs.getString("id"),
                        LocalDate.parse(rs.getString("trade_date")),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("side"),
                        rs.getDouble("price"),
                        rs.getInt("quantity"),
                        rs.getDouble("amount"),
                        rs.getDouble("fee"),
                        rs.getString("source_file"),
                        rs.getString("imported_at") != null ? OffsetDateTime.parse(rs.getString("imported_at")) : null
                ),
                date.toString()
        );
    }

    private void saveRecord(TradeRecord record) {
        jdbc.update(
                "INSERT INTO trade_record (id, trade_date, code, name, side, price, quantity, amount, fee, source_file, imported_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO NOTHING",
                record.id(),
                record.tradeDate().toString(),
                record.code(),
                record.name(),
                record.side(),
                record.price(),
                record.quantity(),
                record.amount(),
                record.fee(),
                record.sourceFile(),
                record.importedAt() != null ? record.importedAt().toString() : null
        );
    }

    private List<LocalDate> listDates() {
        return jdbc.query(
                "SELECT DISTINCT trade_date FROM trade_record ORDER BY trade_date",
                (rs, rowNum) -> LocalDate.parse(rs.getString("trade_date"))
        );
    }

    private boolean sameTrade(TradeRecord left, TradeRecord right) {
        return left.tradeDate().equals(right.tradeDate())
                && left.code().equals(right.code())
                && left.side().equals(right.side())
                && Double.compare(left.price(), right.price()) == 0
                && left.quantity() == right.quantity()
                && Double.compare(left.amount(), right.amount()) == 0;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace("/", "-").replace(".", "-");
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yyyyMMdd"),
                DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
        );
        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String text = value.replace(",", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private char detectDelimiter(String headerLine) {
        if (headerLine.contains("\t")) {
            return '\t';
        }
        if (headerLine.contains(";")) {
            return ';';
        }
        return ',';
    }

    private List<String> splitLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == delimiter && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        result.add(current.toString());
        return result;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
