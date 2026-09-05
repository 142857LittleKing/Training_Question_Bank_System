package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.ReviewAction;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.dto.ReviewDtos.ReviewActionReq;
import com.questionbank.dto.ReviewDtos.ReviewRecordView;
import com.questionbank.service.QuestionService;
import com.questionbank.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 审核中心: 通过/驳回 + 审核记录查询 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final QuestionService questionService;

    public ReviewController(ReviewService reviewService, QuestionService questionService) {
        this.reviewService = reviewService;
        this.questionService = questionService;
    }

    /** 审核通过 -> 上架 */
    @PostMapping("/review/{questionId}/approve")
    public ApiResponse<QuestionView> approve(@PathVariable Long questionId,
                                             @RequestBody(required = false) ReviewActionReq req) {
        String comment = req == null ? null : req.comment();
        return ApiResponse.ok(questionService.toView(reviewService.approve(questionId, comment)));
    }

    /** 审核驳回 */
    @PostMapping("/review/{questionId}/reject")
    public ApiResponse<QuestionView> reject(@PathVariable Long questionId,
                                            @RequestBody(required = false) ReviewActionReq req) {
        String comment = req == null ? null : req.comment();
        return ApiResponse.ok(questionService.toView(reviewService.reject(questionId, comment)));
    }

    /** 审核记录分页 */
    @GetMapping("/review-records")
    public ApiResponse<PageResult<ReviewRecordView>> records(
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) ReviewAction action,
            @RequestParam(required = false) String reviewerName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reviewService.pageRecords(questionId, action, reviewerName, page, size));
    }

    /** 某道题的审核历史 */
    @GetMapping("/review-records/question/{questionId}")
    public ApiResponse<List<ReviewRecordView>> byQuestion(@PathVariable Long questionId) {
        return ApiResponse.ok(reviewService.listByQuestion(questionId));
    }
}
