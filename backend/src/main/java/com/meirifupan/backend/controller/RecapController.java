package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.AiBriefing;
import com.meirifupan.backend.model.AiInsight;
import com.meirifupan.backend.model.AiSummary;
import com.meirifupan.backend.model.CaptureRequest;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIntelligence;
import com.meirifupan.backend.model.RecapDetailResponse;
import com.meirifupan.backend.model.RecapListItem;
import com.meirifupan.backend.service.AiBriefingService;
import com.meirifupan.backend.service.AiInsightService;
import com.meirifupan.backend.service.AiSummaryService;
import com.meirifupan.backend.service.IndicatorService;
import com.meirifupan.backend.service.MarketIntelligenceService;
import com.meirifupan.backend.service.RecapCaptureService;
import com.meirifupan.backend.service.RecapStorageService;
import com.meirifupan.backend.service.TradePlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/recaps")
public class RecapController {
    // AI-READABLE-MAIN-ENTRY:
    // Primary recap API entry.
    // Recommended read order for future AI development:
    // RecapController -> RecapCaptureService -> Provider/Python scripts
    // -> IndicatorService -> TradePlanService -> MarketIntelligenceService
    // -> AiBriefingService / AiInsightService / AiSummaryService

    private final RecapStorageService storageService;
    private final RecapCaptureService captureService;
    private final IndicatorService indicatorService;
    private final TradePlanService tradePlanService;
    private final AiSummaryService aiSummaryService;
    private final AiInsightService aiInsightService;
    private final AiBriefingService aiBriefingService;
    private final MarketIntelligenceService marketIntelligenceService;

    public RecapController(RecapStorageService storageService,
                           RecapCaptureService captureService,
                           IndicatorService indicatorService,
                           TradePlanService tradePlanService,
                           AiSummaryService aiSummaryService,
                           AiInsightService aiInsightService,
                           AiBriefingService aiBriefingService,
                           MarketIntelligenceService marketIntelligenceService) {
        this.storageService = storageService;
        this.captureService = captureService;
        this.indicatorService = indicatorService;
        this.tradePlanService = tradePlanService;
        this.aiSummaryService = aiSummaryService;
        this.aiInsightService = aiInsightService;
        this.aiBriefingService = aiBriefingService;
        this.marketIntelligenceService = marketIntelligenceService;
    }

    @GetMapping
    public List<RecapListItem> list() {
        return storageService.list();
    }

    @GetMapping("/{tradeDate}")
    public RecapDetailResponse detail(@PathVariable LocalDate tradeDate) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        return buildResponse(report);
    }

    @PostMapping("/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public RecapDetailResponse capture(@Valid @RequestBody CaptureRequest request) {
        DailyRecapReport report = captureService.capture(request.tradeDate());
        return buildResponse(report);
    }

    @GetMapping("/{tradeDate}/ai-summary")
    public AiSummary aiSummary(@PathVariable LocalDate tradeDate,
                               @RequestParam(defaultValue = "false") boolean refresh) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        List<DailyRecapReport> recentReports = storageService.loadRecent(report.tradeDate(), 20);
        var indicators = indicatorService.calculate(report, recentReports);
        var tradePlan = tradePlanService.buildPlan(report, indicators);
        return aiSummaryService.generateOrLoad(report, indicators, tradePlan, refresh);
    }

    @GetMapping("/{tradeDate}/ai-insight")
    public AiInsight aiInsight(@PathVariable LocalDate tradeDate,
                               @RequestParam(defaultValue = "false") boolean refresh) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        List<DailyRecapReport> recentReports = storageService.loadRecent(report.tradeDate(), 20);
        var indicators = indicatorService.calculate(report, recentReports);
        var tradePlan = tradePlanService.buildPlan(report, indicators);
        return aiInsightService.generateOrLoad(report, indicators, tradePlan, refresh);
    }

    @GetMapping("/{tradeDate}/ai-briefing")
    public AiBriefing aiBriefing(@PathVariable LocalDate tradeDate,
                                 @RequestParam(defaultValue = "false") boolean refresh) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        List<DailyRecapReport> recentReports = storageService.loadRecent(report.tradeDate(), 20);
        var indicators = indicatorService.calculate(report, recentReports);
        var tradePlan = tradePlanService.buildPlan(report, indicators);
        return aiBriefingService.generateOrLoad(report, recentReports, indicators, tradePlan, refresh);
    }

    @GetMapping("/{tradeDate}/market-intelligence")
    public MarketIntelligence marketIntelligence(@PathVariable LocalDate tradeDate,
                                                 @RequestParam(defaultValue = "false") boolean refresh) {
        return marketIntelligenceService.loadOrCollect(tradeDate, refresh);
    }

    private RecapDetailResponse buildResponse(DailyRecapReport report) {
        List<DailyRecapReport> recentReports = storageService.loadRecent(report.tradeDate(), 20);
        var indicators = indicatorService.calculate(report, recentReports);
        var tradePlan = tradePlanService.buildPlan(report, indicators);
        var trendPoints = recentReports.stream()
                .map(indicatorService::toTrendPoint)
                .toList();
        return new RecapDetailResponse(report, indicators, tradePlan, trendPoints);
    }
}
