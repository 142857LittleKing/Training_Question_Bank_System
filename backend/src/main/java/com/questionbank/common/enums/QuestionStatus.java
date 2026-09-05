package com.questionbank.common.enums;

/** 题目状态 - 对应 生成->审核->上架 流程 */
public enum QuestionStatus {

    DRAFT("草稿"),
    GENERATED("AI生成待处理"),
    PENDING("待审核"),
    PUBLISHED("已上架"),
    REJECTED("已驳回"),
    OFFLINE("已下架");

    private final String label;

    QuestionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
