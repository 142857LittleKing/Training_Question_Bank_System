package com.questionbank.dto;

import com.questionbank.common.enums.QuestionStatus;

import java.util.List;

/** 批量导入 DTO */
public final class ImportDtos {

    private ImportDtos() {
    }

    public record ImportRowResult(int rowNo, boolean success, String message, Long questionId) {
    }

    public record ImportResult(String fileName, int totalRows, int success, int failed,
                               QuestionStatus defaultStatus, List<ImportRowResult> rows) {
    }

    public record ImportJsonReq(String content, QuestionStatus defaultStatus) {
    }
}
