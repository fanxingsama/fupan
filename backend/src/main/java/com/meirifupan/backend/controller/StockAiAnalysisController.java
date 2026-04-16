package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.StockAiAnalysisRequest;
import com.meirifupan.backend.model.StockAiAnalysisResponse;
import com.meirifupan.backend.service.StockAiAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stock-ai-analysis")
public class StockAiAnalysisController {

    private final StockAiAnalysisService stockAiAnalysisService;

    public StockAiAnalysisController(StockAiAnalysisService stockAiAnalysisService) {
        this.stockAiAnalysisService = stockAiAnalysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockAiAnalysisResponse analyze(@Valid @RequestBody StockAiAnalysisRequest request) {
        try {
            return stockAiAnalysisService.analyze(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
