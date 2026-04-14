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

/**
 * 复盘 REST 控制器 —— 提供复盘数据的查询和采集触发 API。
 * <p>
 * 接口一览：
 * <ul>
 *   <li>GET  /api/recaps           —— 返回所有已生成复盘的交易日列表（轻量摘要）</li>
 *   <li>GET  /api/recaps/{tradeDate} —— 返回指定交易日的完整复盘报告</li>
 *   <li>POST /api/recaps/capture    —— 触发指定交易日的数据采集，采集完成后返回报告</li>
 * </ul>
 * 前端通过这三个接口实现：查看历史复盘、加载详情、触发新采集。
 */
@RestController
@RequestMapping("/api/recaps")
public class RecapController {

    private final RecapStorageService storageService;
    private final RecapCaptureService captureService;

    public RecapController(RecapStorageService storageService, RecapCaptureService captureService) {
        this.storageService = storageService;
        this.captureService = captureService;
    }

    /**
     * 返回本地已经生成过复盘数据的交易日列表。
     */
    @GetMapping
    public List<RecapListItem> list() {
        return storageService.list();
    }

    /**
     * 读取某一天已经保存在本地 json 文件里的复盘详情。
     */
    @GetMapping("/{tradeDate}")
    public DailyRecapReport detail(@PathVariable LocalDate tradeDate) {
        return storageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该日期复盘"));
    }

    /**
     * 触发一次指定交易日的采集，并把采集结果直接返回给前端。
     */
    @PostMapping("/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyRecapReport capture(@Valid @RequestBody CaptureRequest request) {
        return captureService.capture(request.tradeDate());
    }
}
