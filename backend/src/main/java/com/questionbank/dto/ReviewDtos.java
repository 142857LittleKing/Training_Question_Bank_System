package com.questionbank.dto;

import com.questionbank.common.enums.ReviewAction;

import java.time.LocalDateTime;

/** 审核相关 DTO */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** 通过/驳回操作: comment 非必填(驳回时服务端强制必填) */
    public record ReviewActionReq(String comment) {
    }

    public record ReviewRecordView(Long id, Long questionId, String stemSnapshot,
                                   String reviewerName, ReviewAction action, String actionLabel,
                                   String comment, LocalDateTime createdAt) {
    }
}
