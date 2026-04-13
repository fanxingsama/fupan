package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.CaptureRequest;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.RecapListItem;
import com.meirifupan.backend.service.RecapCaptureService;
import com.meirifupan.backend.service.RecapStorageService;
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

@RestController
@RequestMapping("/api/recaps")
public class RecapController {

    private final RecapStorageService storageService;
    private final RecapCaptureService captureService;

    public RecapController(RecapStorageService storageService, RecapCaptureService captureService) {
        this.storageService = storageService;
        this.captureService = captureService;
    }

    @GetMapping
    public List<RecapListItem> list() {
        return storageService.list();
    }

    @GetMapping("/{tradeDate}")
    public DailyRecapReport detail(@PathVariable LocalDate tradeDate) {
        return storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
    }

    @PostMapping("/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyRecapReport capture(@Valid @RequestBody CaptureRequest request) {
        return captureService.capture(request.tradeDate());
    }
}
