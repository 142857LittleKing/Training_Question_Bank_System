package com.questionbank.entity;

import com.questionbank.common.enums.ReviewAction;
import jakarta.persistence.*;

/** 审核记录 */
@Entity
@Table(name = "review_record", indexes = {
        @Index(name = "idx_rr_question", columnList = "question_id"),
        @Index(name = "idx_rr_reviewer", columnList = "reviewer_id")
})
public class ReviewRecord extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 题干快照(便于列表展示, 不因原题被修改而丢失) */
    @Column(name = "stem_snapshot", length = 300)
    private String stemSnapshot;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(length = 600)
    private String comment;

    public ReviewRecord() {
    }

    public ReviewRecord(Long questionId, String stemSnapshot, Long reviewerId, String reviewerName,
                        ReviewAction action, String comment) {
        this.questionId = questionId;
        this.stemSnapshot = stemSnapshot;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.action = action;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getStemSnapshot() {
        return stemSnapshot;
    }

    public void setStemSnapshot(String stemSnapshot) {
        this.stemSnapshot = stemSnapshot;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public ReviewAction getAction() {
        return action;
    }

    public void setAction(ReviewAction action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
