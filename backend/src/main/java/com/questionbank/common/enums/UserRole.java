package com.questionbank.common.enums;

/** 系统角色 */
public enum UserRole {

    ADMIN("系统管理员"),
    GENERATOR("出题/录入员"),
    REVIEWER("审核员");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
