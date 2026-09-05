package com.questionbank.service;

import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.ReviewAction;
import com.questionbank.common.enums.UserRole;
import com.questionbank.dto.ReviewDtos.ReviewRecordView;
import com.questionbank.entity.Question;
import com.questionbank.entity.ReviewRecord;
import com.questionbank.repository.QuestionRepository;
import com.questionbank.repository.ReviewRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 生成-审核-上架流程中的 审核环节 */
@Service
public class ReviewService {

    private final QuestionRepository questionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final QuestionService questionService;

    public ReviewService(QuestionRepository questionRepository,
                         ReviewRecordRepository reviewRecordRepository,
                         QuestionService questionService) {
        this.questionRepository = questionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.questionService = questionService;
    }

    /** 审核通过 -> 上架 */
    @Transactional
    public Question approve(Long questionId, String comment) {
        UserContext.requireRole(UserRole.REVIEWER, UserRole.ADMIN);
        Question q = requirePending(questionId);
        String reviewer = UserContext.required().getName();
        q.setStatus(QuestionStatus.PUBLISHED);
        q.setReviewerName(reviewer);
        q.setReviewComment(StringUtils.hasText(comment) ? comment.trim() : "审核通过");
        q.setPublishedAt(LocalDateTime.now());
        questionRepository.save(q);
        reviewRecordRepository.save(new ReviewRecord(q.getId(), snapshot(q), null, reviewer,
                ReviewAction.PASS, StringUtils.hasText(comment) ? comment.trim() : null));
        return q;
    }

    /** 审核驳回 */
    @Transactional
    public Question reject(Long questionId, String comment) {
        UserContext.requireRole(UserRole.REVIEWER, UserRole.ADMIN);
        if (!StringUtils.hasText(comment)) {
            throw BizException.bad("驳回时必须填写驳回原因");
        }
        Question q = requirePending(questionId);
        String reviewer = UserContext.required().getName();
        q.setStatus(QuestionStatus.REJECTED);
        q.setReviewerName(reviewer);
        q.setReviewComment(comment.trim());
        questionRepository.save(q);
        reviewRecordRepository.save(new ReviewRecord(q.getId(), snapshot(q), null, reviewer,
                ReviewAction.REJECT, comment.trim()));
        return q;
    }

    private Question requirePending(Long questionId) {
        Question q = questionService.getRequired(questionId);
        if (q.getStatus() != QuestionStatus.PENDING) {
            throw BizException.bad("题目当前不在待审核状态(当前: " + q.getStatus().getLabel() + ")");
        }
        return q;
    }

    private String snapshot(Question q) {
        String stem = q.getStem();
        return stem.length() <= 300 ? stem : stem.substring(0, 300);
    }

    /** 审核记录分页查询 */
    @Transactional(readOnly = true)
    public PageResult<ReviewRecordView> pageRecords(Long questionId, ReviewAction action,
                                                    String reviewerName, int page, int size) {
        Specification<ReviewRecord> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (questionId != null) {
                predicates.add(cb.equal(root.get("questionId"), questionId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (StringUtils.hasText(reviewerName)) {
                predicates.add(cb.like(root.get("reviewerName"), "%" + reviewerName.trim() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<ReviewRecord> result = reviewRecordRepository.findAll(spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(), page, size);
    }

    /** 某道题的审核历史 */
    @Transactional(readOnly = true)
    public List<ReviewRecordView> listByQuestion(Long questionId) {
        return reviewRecordRepository.findByQuestionIdOrderByCreatedAtDesc(questionId)
                .stream().map(this::toView).toList();
    }

    public ReviewRecordView toView(ReviewRecord r) {
        return new ReviewRecordView(r.getId(), r.getQuestionId(), r.getStemSnapshot(),
                r.getReviewerName(), r.getAction(),
                r.getAction() == null ? null : r.getAction().getLabel(),
                r.getComment(), r.getCreatedAt());
    }
}
