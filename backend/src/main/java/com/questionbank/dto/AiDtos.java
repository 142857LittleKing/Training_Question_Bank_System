package com.questionbank.dto;

import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 出题相关 DTO */
public final class AiDtos {

    private AiDtos() {
    }

    /**
     * 出题请求: 选择知识点(type=null 表示按各题型自动配比生成), count 每题型数量
     */
    public record GenerateReq(
            @NotNull(message = "请选择知识点") Long knowledgePointId,
            QuestionType type,
            @Min(value = 1, message = "数量 1~30") @Max(value = 30, message = "数量 1~30") int count,
            @Min(value = 1, message = "难度 1~5") @Max(value = 5, message = "难度 1~5") Integer difficulty,
            @Size(max = 500, message = "附加要求最长500字符") String extraInstruction) {
    }

    /** 单道生成结果: valid=false 时 reasons 说明格式校验失败原因 */
    public record GenerationItemView(int index, boolean valid, List<String> reasons,
                                     boolean duplicate, QuestionPayload question) {
    }

    /** 一次出题请求的完整结果 */
    public record GenerationResult(
            String provider,
            String providerLabel,
            String modeTip,
            int requested,
            int validCount,
            List<GenerationItemView> items) {
    }

    /** 把校验通过的生成题批量存为 GENERATED 状态 */
    public record SaveGeneratedReq(
            List<QuestionPayload> questions,
            @Size(max = 100, message = "批次名最长100字符") String batchLabel) {
    }

    /** 保存结果 */
    public record SaveGeneratedResult(int requested, int saved, int skippedInvalid, int skippedDuplicate,
                                      String batch) {
    }
}
