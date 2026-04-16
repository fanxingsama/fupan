package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.UserStockAnalysisResponse;
import com.meirifupan.backend.service.UserStockAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/user-stock-analysis")
public class UserStockAnalysisController {

    private final UserStockAnalysisService userStockAnalysisService;

    public UserStockAnalysisController(UserStockAnalysisService userStockAnalysisService) {
        this.userStockAnalysisService = userStockAnalysisService;
    }

    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public UserStockAnalysisResponse analyze(@RequestPart("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少上传一张图片");
        }
        try {
            return userStockAnalysisService.analyze(files);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
