package com.questionbank.entity;

import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 题目 */
@Entity
@Table(name = "question", indexes = {
        @Index(name = "idx_q_status", columnList = "status"),
        @Index(name = "idx_q_type", columnList = "type"),
        @Index(name = "idx_q_kp", columnList = "knowledge_point_id"),
        @Index(name = "idx_q_created_by", columnList = "created_by")
})
public class Question extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    /** JSON 数组字符串, 如 ["选项A","选项B"...]; 判断/简答题为 null */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** 单选: "B"; 多选: "A,C"; 判断: "对"/"错"; 简答: 参考答案文本 */
    @Column(nullable = false, length = 600)
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(nullable = false)
    private Integer difficulty = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "knowledge_point_id", nullable = false)
    private KnowledgePoint knowledgePoint;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    @Column(name = "review_comment", length = 600)
    private String reviewComment;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 出题流水号(一次 AI 出题会话), 便于追溯 */
    @Column(name = "generate_batch")
    private String generateBatch;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public QuestionSource getSource() {
        return source;
    }

    public void setSource(QuestionSource source) {
        this.source = source;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
    }

    public KnowledgePoint getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(KnowledgePoint knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getGenerateBatch() {
        return generateBatch;
    }

    public void setGenerateBatch(String generateBatch) {
        this.generateBatch = generateBatch;
    }
}
