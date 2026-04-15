package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.CaptureRequest;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.RecapDetailResponse;
import com.meirifupan.backend.model.RecapListItem;
import com.meirifupan.backend.model.AiSummary;
import com.meirifupan.backend.service.IndicatorService;
import com.meirifupan.backend.service.RecapCaptureService;
import com.meirifupan.backend.service.RecapStorageService;
import com.meirifupan.backend.service.AiSummaryService;
import com.meirifupan.backend.service.TradePlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * 复盘 REST 控制器 —— 提供复盘数据的查询和采集触发 API。
 * <p>
 * 接口一览：
 * <ul>
 *   <li>GET  /api/recaps           —— 返回所有已生成复盘的交易日列表（轻量摘要）</li>
 *   <li>GET  /api/recaps/{tradeDate} —— 返回指定交易日的完整复盘报告（含计算指标和趋势数据）</li>
 *   <li>POST /api/recaps/capture    —— 触发指定交易日的数据采集，采集完成后返回报告（含计算指标和趋势数据）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/recaps")
public class RecapController {

    private final RecapStorageService storageService;
    private final RecapCaptureService captureService;
    private final IndicatorService indicatorService;
    private final TradePlanService tradePlanService;
    private final AiSummaryService aiSummaryService;

    public RecapController(RecapStorageService storageService,
                           RecapCaptureService captureService,
                           IndicatorService indicatorService,
                           TradePlanService tradePlanService,
                           AiSummaryService aiSummaryService) {
        this.storageService = storageService;
        this.captureService = captureService;
        this.indicatorService = indicatorService;
        this.tradePlanService = tradePlanService;
        this.aiSummaryService = aiSummaryService;
    }

    @GetMapping
    public List<RecapListItem> list() {
        return storageService.list();
    }

    /**
     * 返回指定交易日的完整复盘报告，包含后端计算好的指标和趋势数据。
     */
    @GetMapping("/{tradeDate}")
    public RecapDetailResponse detail(@PathVariable LocalDate tradeDate) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        return buildResponse(report);
    }

    /**
     * 触发采集并返回包含指标和趋势数据的完整响应。
     */
    @PostMapping("/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public RecapDetailResponse capture(@Valid @RequestBody CaptureRequest request) {
        DailyRecapReport report = captureService.capture(request.tradeDate());
        return buildResponse(report);
    }

    @GetMapping("/{tradeDate}/ai-summary")
    public AiSummary aiSummary(@PathVariable LocalDate tradeDate, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean refresh) {
        DailyRecapReport report = storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
        List<DailyRecapReport> recentReports = storageService.loadRecent(report.tradeDate(), 20);
        var indicators = indicatorService.calculate(report, recentReports);
        var tradePlan = tradePlanService.buildPlan(report, indicators);
        return aiSummaryService.generateOrLoad(report, indicators, tradePlan, refresh);
    }

    /**
     * 加载最近的历史报告，计算指标和趋势，打包为统一响应体。
     */
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
