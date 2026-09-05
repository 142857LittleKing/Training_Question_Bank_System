package com.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 知识点相关 DTO */
public final class KnowledgePointDtos {

    private KnowledgePointDtos() {
    }

    public record KnowledgePointReq(
            @NotBlank(message = "知识点名称不能为空") @Size(max = 100, message = "名称最长100字符") String name,
            @Size(max = 50, message = "分类最长50字符") String category,
            @Size(max = 500, message = "描述最长500字符") String description) {
    }

    public record KnowledgePointView(Long id, String name, String category, String description,
                                     String createdBy, long questionCount, LocalDateTime createdAt) {
    }
}
