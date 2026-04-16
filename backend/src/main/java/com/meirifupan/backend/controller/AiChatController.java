package com.meirifupan.backend.controller;

import com.meirifupan.backend.model.AiChatRequest;
import com.meirifupan.backend.model.AiChatResponse;
import com.meirifupan.backend.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.chat(request);
    }
}
