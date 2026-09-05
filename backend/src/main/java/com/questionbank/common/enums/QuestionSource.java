package com.questionbank.common.enums;

/** 题目来源 */
public enum QuestionSource {

    MANUAL("手工录入"),
    AI("大模型生成"),
    IMPORT("批量导入");

    private final String label;

    QuestionSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
