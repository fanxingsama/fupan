package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.ThemeTrackingDetail;
import com.meirifupan.backend.model.ThemeTrackingHistoryItem;
import com.meirifupan.backend.model.ThemeTrackingSummary;
import com.meirifupan.backend.service.ThemeTrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/recaps/{tradeDate}/theme-tracking")
public class ThemeTrackingController {

    private final ThemeTrackingService themeTrackingService;

    public ThemeTrackingController(ThemeTrackingService themeTrackingService) {
        this.themeTrackingService = themeTrackingService;
    }

    @GetMapping
    public List<ThemeTrackingSummary> list(@PathVariable LocalDate tradeDate,
                                           @RequestParam(defaultValue = "false") boolean refresh) {
        return themeTrackingService.list(tradeDate, refresh);
    }

    @GetMapping("/{themeName}")
    public ThemeTrackingDetail detail(@PathVariable LocalDate tradeDate,
                                      @PathVariable String themeName,
                                      @RequestParam(defaultValue = "false") boolean refresh) {
        return themeTrackingService.detail(tradeDate, themeName, refresh);
    }

    @GetMapping("/{themeName}/history")
    public List<ThemeTrackingHistoryItem> history(@PathVariable LocalDate tradeDate,
                                                  @PathVariable String themeName,
                                                  @RequestParam(defaultValue = "10") int days) {
        return themeTrackingService.history(tradeDate, themeName, days);
    }

    @PostMapping("/refresh")
    public List<ThemeTrackingSummary> refresh(@PathVariable LocalDate tradeDate) {
        return themeTrackingService.list(tradeDate, true);
    }
}
