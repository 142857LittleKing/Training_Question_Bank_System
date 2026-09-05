package com.questionbank.controller;

import com.questionbank.ai.AiProvider.ProviderInfo;
import com.questionbank.ai.AiProviderFactory;
import com.questionbank.common.ApiResponse;
import com.questionbank.dto.AiDtos.GenerateReq;
import com.questionbank.dto.AiDtos.GenerationResult;
import com.questionbank.dto.AiDtos.SaveGeneratedReq;
import com.questionbank.dto.AiDtos.SaveGeneratedResult;
import com.questionbank.service.GenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** AI 智能出题 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GenerationService generationService;
    private final AiProviderFactory providerFactory;

    public AiController(GenerationService generationService, AiProviderFactory providerFactory) {
        this.generationService = generationService;
        this.providerFactory = providerFactory;
    }

    /** 出题源当前状态 */
    @GetMapping("/provider")
    public ApiResponse<ProviderInfo> provider() {
        return ApiResponse.ok(providerFactory.info());
    }

    /** 按知识点出题(不落库, 返回逐题格式校验结果) */
    @PostMapping("/generate")
    public ApiResponse<GenerationResult> generate(@Valid @RequestBody GenerateReq req) {
        return ApiResponse.ok(generationService.generate(req));
    }

    /** 保存通过校验的题目为 GENERATED(待审核) */
    @PostMapping("/save")
    public ApiResponse<SaveGeneratedResult> save(@RequestBody SaveGeneratedReq req) {
        return ApiResponse.ok(generationService.save(req));
    }
}
