package com.questionbank.dto;

import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 题目相关 DTO(入参与出参视图) */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    /**
     * 题目的统一载荷: 手工录入 / AI 生成 / 批量导入 共用,
     * 通过 {@link com.questionbank.service.QuestionFormat} 做格式校验。
     */
    public record QuestionPayload(
            @NotNull(message = "题型不能为空") QuestionType type,
            @NotBlank(message = "题干不能为空") @Size(max = 3000, message = "题干最长3000字符") String stem,
            List<@Size(max = 500, message = "选项最长500字符") String> options,
            @NotBlank(message = "答案不能为空") @Size(max = 2000, message = "答案最长2000字符") String answer,
            @Size(max = 3000, message = "解析最长3000字符") String analysis,
            @Min(value = 1, message = "难度1~5") @Max(value = 5, message = "难度1~5") Integer difficulty,
            Long knowledgePointId,
            @Size(max = 100, message = "知识点名称最长100字符") String knowledgePointName) {
    }

    /** 题目列表查询参数 */
    public record QuestionQuery(String keyword, Long kpId, QuestionType type,
                                QuestionStatus status, QuestionSource source,
                                Boolean mine, int page, int size) {
    }

    /** 题目展示视图(扁平结构, 含知识点信息) */
    public record QuestionView(
            Long id,
            QuestionType type, String typeLabel,
            QuestionStatus status, String statusLabel,
            QuestionSource source, String sourceLabel,
            String stem,
            List<String> options,
            String answer,
            String analysis,
            Integer difficulty,
            Long knowledgePointId, String knowledgePointName, String knowledgePointCategory,
            String createdBy,
            String reviewerName, String reviewComment,
            LocalDateTime submittedAt, LocalDateTime publishedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
