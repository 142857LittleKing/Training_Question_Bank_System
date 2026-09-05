package com.questionbank.common.enums;

/** 审核动作 */
public enum ReviewAction {

    PASS("审核通过"),
    REJECT("审核驳回"),
    IMPORT_PASS("批量导入自动通过");

    private final String label;

    ReviewAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
