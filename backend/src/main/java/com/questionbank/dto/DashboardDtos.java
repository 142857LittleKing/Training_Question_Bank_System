package com.questionbank.dto;

import java.util.List;

/** 首页统计 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** 名称-数量(名称可直接作为前端 key / 下拉) */
    public record NameCount(String name, String label, long count) {
    }

    /** 知识点维度统计 */
    public record KpCount(Long id, String name, long count) {
    }

    public record DashboardSummary(
            long total,
            long totalPublished,
            long pendingReview,
            List<NameCount> byStatus,
            List<NameCount> byType,
            List<NameCount> bySource,
            List<KpCount> topKnowledgePoints,
            List<QuestionDtos.QuestionView> recent) {
    }
}
