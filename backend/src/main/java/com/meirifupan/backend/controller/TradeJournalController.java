package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.TradeImportResponse;
import com.meirifupan.backend.model.TradeJournalDay;
import com.meirifupan.backend.service.TradeJournalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/trade-journal")
public class TradeJournalController {

    private final TradeJournalService tradeJournalService;

    public TradeJournalController(TradeJournalService tradeJournalService) {
        this.tradeJournalService = tradeJournalService;
    }

    @GetMapping
    public List<TradeJournalDay> list() {
        return tradeJournalService.listJournal();
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeImportResponse importFile(@RequestPart("file") MultipartFile file) {
        return tradeJournalService.importFile(file);
    }
}
