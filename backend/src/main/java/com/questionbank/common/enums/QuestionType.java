package com.questionbank.common.enums;

import java.util.Arrays;
import java.util.List;

/** 题目类型 */
public enum QuestionType {

    SINGLE("单选题", true),
    MULTIPLE("多选题", true),
    JUDGE("判断题", false),
    SHORT("简答题", false);

    private final String label;
    /** 是否有选项 */
    private final boolean hasOptions;

    QuestionType(String label, boolean hasOptions) {
        this.label = label;
        this.hasOptions = hasOptions;
    }

    public String getLabel() {
        return label;
    }

    public boolean isHasOptions() {
        return hasOptions;
    }

    public static List<QuestionType> valuesOrdered() {
        return Arrays.asList(values());
    }

    /** 兼容中英文/常见别名 */
    public static QuestionType parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase();
        switch (t) {
            case "SINGLE": case "单选": case "单选题": case "SINGLE_CHOICE":
                return SINGLE;
            case "MULTIPLE": case "多选": case "多选题": case "MULTIPLE_CHOICE":
                return MULTIPLE;
            case "JUDGE": case "判断": case "判断题": case "TRUE_FALSE": case "TF":
                return JUDGE;
            case "SHORT": case "简答": case "简答题": case "SHORT_ANSWER": case "QA":
                return SHORT;
            default:
                return null;
        }
    }
}
