package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.service.QuizService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 检索使用(开放接口, 供培训考试/组卷等下游集成) */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    /** 随机抽题 */
    @GetMapping("/random")
    public ApiResponse<List<QuestionView>> random(
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Long kpId,
            @RequestParam(defaultValue = "10") int count) {
        return ApiResponse.ok(quizService.random(type, kpId, count));
    }

    /** 已上架题目检索 */
    @GetMapping("/search")
    public ApiResponse<PageResult<QuestionView>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long kpId,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(quizService.searchPublished(keyword, kpId, type, page, size));
    }
}
