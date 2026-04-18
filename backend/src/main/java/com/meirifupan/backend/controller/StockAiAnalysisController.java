package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.StockAiAnalysisResponse;
import com.meirifupan.backend.service.StockAiAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stock-ai-analysis")
public class StockAiAnalysisController {

    private final StockAiAnalysisService stockAiAnalysisService;

    public StockAiAnalysisController(StockAiAnalysisService stockAiAnalysisService) {
        this.stockAiAnalysisService = stockAiAnalysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockAiAnalysisResponse analyze(@RequestPart("file") MultipartFile file,
                                           @RequestParam("timeframe") String timeframe,
                                           @RequestParam(value = "stockCode", required = false) String stockCode,
                                           @RequestParam(value = "stockName", required = false) String stockName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先上传历史数据文件");
        }
        try {
            return stockAiAnalysisService.analyze(file, timeframe, stockCode, stockName);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
