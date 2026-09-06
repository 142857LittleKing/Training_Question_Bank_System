package com.questionbank.controller;

import com.questionbank.common.ApiResponse;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import com.questionbank.dto.QuestionDtos.QuestionQuery;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.entity.Question;
import com.questionbank.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 题库检索(已上架题也通过本接口按状态检索使用) */
    @GetMapping
    public ApiResponse<PageResult<QuestionView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long kpId,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionSource source,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        QuestionQuery q = new QuestionQuery(keyword, kpId, type, status, source, mine, page, size);
        return ApiResponse.ok(questionService.page(q));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionView> get(@PathVariable Long id) {
        return ApiResponse.ok(questionService.getView(id));
    }

    /** 手工录入题目(保存为草稿) */
    @PostMapping
    public ApiResponse<QuestionView> create(@Valid @RequestBody QuestionPayload payload) {
        return ApiResponse.ok(questionService.toView(questionService.create(payload)));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionView> update(@PathVariable Long id, @Valid @RequestBody QuestionPayload payload) {
        return ApiResponse.ok(questionService.toView(questionService.update(id, payload)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 批量删除: 管理员任意; 出题员仅本人录入且非"已上架/待审核"的题。
     * 逐条校验并返回每条的删除结果与原因, 可部分成功。
     */
    @PostMapping("/batch-delete")
    public ApiResponse<QuestionDtos.BatchDeleteResult> batchDelete(@RequestBody QuestionDtos.BatchDeleteReq req) {
        return ApiResponse.ok(questionService.batchDelete(req.ids()));
    }

    /** 提交审核: 草稿/AI生成/已驳回 -> 待审核 */
    @PostMapping("/{id}/submit")
    public ApiResponse<QuestionView> submit(@PathVariable Long id) {
        return ApiResponse.ok(questionService.toView(questionService.submitToReview(id)));
    }

    /** 下架(管理员/审核员) */
    @PostMapping("/{id}/offline")
    public ApiResponse<QuestionView> offline(@PathVariable Long id) {
        return ApiResponse.ok(questionService.toView(questionService.offline(id)));
    }
}
