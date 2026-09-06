package com.questionbank.service;

import com.questionbank.auth.CurrentUser;
import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.UserRole;
import com.questionbank.dto.QuestionDtos.BatchDeleteItem;
import com.questionbank.dto.QuestionDtos.BatchDeleteResult;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import com.questionbank.dto.QuestionDtos.QuestionQuery;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.entity.Question;
import com.questionbank.repository.QuestionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final KnowledgePointService knowledgePointService;

    public QuestionService(QuestionRepository questionRepository, KnowledgePointService knowledgePointService) {
        this.questionRepository = questionRepository;
        this.knowledgePointService = knowledgePointService;
    }

    /** 题库检索(支持 知识点/题型/状态/来源/关键词, 供审核上架后检索使用) */
    @Transactional(readOnly = true)
    public PageResult<QuestionView> page(QuestionQuery q) {
        Specification<Question> spec = buildSpec(q);
        Page<Question> result = questionRepository.findAll(spec, PageRequest.of(
                q.page(), Math.min(Math.max(q.size(), 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResult.of(result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(), q.page(), q.size());
    }

    private Specification<Question> buildSpec(QuestionQuery q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(q.keyword())) {
                predicates.add(cb.like(root.get("stem"), "%" + q.keyword().trim() + "%"));
            }
            if (q.kpId() != null) {
                predicates.add(cb.equal(root.get("knowledgePoint").get("id"), q.kpId()));
            }
            if (q.type() != null) {
                predicates.add(cb.equal(root.get("type"), q.type()));
            }
            if (q.status() != null) {
                predicates.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.source() != null) {
                predicates.add(cb.equal(root.get("source"), q.source()));
            }
            if (Boolean.TRUE.equals(q.mine())) {
                predicates.add(cb.equal(root.get("createdBy"), UserContext.required().getName()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public QuestionView getView(Long id) {
        return toView(getRequired(id));
    }

    public Question getRequired(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("题目不存在: id=" + id));
    }

    /** 内部保存(供手工录入/AI生成/导入复用): 校验通过才落库 */
    @Transactional
    public Question saveFromPayload(QuestionPayload raw, QuestionSource source, QuestionStatus status,
                                    String generateBatch, boolean checkDuplicate) {
        QuestionPayload p = QuestionFormat.normalize(raw);
        List<String> reasons = QuestionFormat.validate(p);
        if (!reasons.isEmpty()) {
            throw BizException.bad("题目格式校验不通过: " + String.join("; ", reasons));
        }
        if (checkDuplicate) {
            questionRepository.findFirstByStem(p.stem()).ifPresent(exist -> {
                throw BizException.bad("与已有题目(题号 " + exist.getId() + ")题干重复");
            });
        }
        Question q = new Question();
        applyPayload(q, p);
        q.setSource(source == null ? QuestionSource.MANUAL : source);
        q.setStatus(status == null ? QuestionStatus.DRAFT : status);
        q.setGenerateBatch(generateBatch);
        if (status == QuestionStatus.PENDING) {
            q.setSubmittedAt(LocalDateTime.now());
        }
        q.setCreatedBy(UserContext.required().getName());
        return questionRepository.save(q);
    }

    /** 手工录入 */
    @Transactional
    public Question create(QuestionPayload raw) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        return saveFromPayload(raw, QuestionSource.MANUAL, QuestionStatus.DRAFT, null, true);
    }

    /** 编辑题目 */
    @Transactional
    public Question update(Long id, QuestionPayload raw) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        Question q = getRequired(id);
        if (q.getStatus() == QuestionStatus.PUBLISHED) {
            throw BizException.bad("已上架题目不可直接编辑, 请先下架");
        }
        if (q.getStatus() == QuestionStatus.PENDING) {
            throw BizException.bad("题目正在审核中, 不可编辑");
        }
        QuestionPayload p = QuestionFormat.normalize(raw);
        List<String> reasons = QuestionFormat.validate(p);
        if (!reasons.isEmpty()) {
            throw BizException.bad("题目格式校验不通过: " + String.join("; ", reasons));
        }
        questionRepository.findFirstByStem(p.stem())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BizException.bad("与已有题目(题号 " + other.getId() + ")题干重复");
                });
        applyPayload(q, p);
        return questionRepository.save(q);
    }

    private void applyPayload(Question q, QuestionPayload p) {
        KnowledgePoint kp;
        if (p.knowledgePointId() != null) {
            kp = knowledgePointService.getRequired(p.knowledgePointId());
        } else {
            kp = knowledgePointService.getOrCreateByName(p.knowledgePointName(), "培训");
        }
        q.setKnowledgePoint(kp);
        q.setType(p.type());
        q.setStem(p.stem());
        q.setOptionsJson(QuestionFormat.optionsToJson(p.options()));
        q.setAnswer(p.answer());
        q.setAnalysis(p.analysis() == null || p.analysis().isBlank() ? null : p.analysis());
        q.setDifficulty(p.difficulty());
    }

    /** 提交审核: 草稿/AI生成/已驳回 -> 待审核 */
    @Transactional
    public Question submitToReview(Long id) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        Question q = getRequired(id);
        if (q.getStatus() != QuestionStatus.DRAFT
                && q.getStatus() != QuestionStatus.GENERATED
                && q.getStatus() != QuestionStatus.REJECTED
                && q.getStatus() != QuestionStatus.OFFLINE) {
            throw BizException.bad("当前状态(" + q.getStatus().getLabel() + ")不能提交审核");
        }
        q.setStatus(QuestionStatus.PENDING);
        q.setSubmittedAt(LocalDateTime.now());
        q.setReviewerName(null);
        q.setReviewComment(null);
        return questionRepository.save(q);
    }

    /** 下架 */
    @Transactional
    public Question offline(Long id) {
        UserContext.requireRole(UserRole.ADMIN, UserRole.REVIEWER);
        Question q = getRequired(id);
        if (q.getStatus() != QuestionStatus.PUBLISHED) {
            throw BizException.bad("仅已上架题目可以下架");
        }
        q.setStatus(QuestionStatus.OFFLINE);
        return questionRepository.save(q);
    }

    /** 删除单个(角色/归属/状态校验失败时抛业务异常) */
    @Transactional
    public void delete(Long id) {
        String err = deleteInternal(id);
        if (err != null) {
            throw BizException.bad(err);
        }
    }

    /**
     * 批量删除: 逐条按同一套规则校验, 可部分成功;
     * 返回每条题目的处理结果与原因, 便于前端展示"哪些删不了、为什么"。
     */
    @Transactional
    public BatchDeleteResult batchDelete(List<Long> ids) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        if (ids == null || ids.isEmpty()) {
            throw BizException.bad("请选择要删除的题目");
        }
        List<Long> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.size() > 500) {
            throw BizException.bad("单次最多批量删除 500 道题目");
        }
        List<BatchDeleteItem> items = new java.util.ArrayList<>();
        int deleted = 0;
        for (Long id : uniqueIds) {
            String err = deleteInternal(id);
            if (err == null) {
                deleted++;
                items.add(new BatchDeleteItem(id, true, null));
            } else {
                items.add(new BatchDeleteItem(id, false, err));
            }
        }
        return new BatchDeleteResult(uniqueIds.size(), deleted, items);
    }

    /** 删除校验 + 执行: 返回 null 表示成功, 否则返回失败原因(不抛异常, 供批量场景逐条收集) */
    private String deleteInternal(Long id) {
        Question q = questionRepository.findById(id).orElse(null);
        if (q == null) {
            return "题目不存在: id=" + id;
        }
        if (UserContext.isAdmin()) {
            questionRepository.delete(q);
            return null;
        }
        CurrentUser cu = UserContext.required();
        if (cu.role() != UserRole.GENERATOR) {
            return "无删除权限, 仅出题员/管理员可删除题目 #" + id;
        }
        if (!q.getCreatedBy().equals(cu.getName())) {
            return "只能删除自己录入的题目 #" + id;
        }
        QuestionStatus st = q.getStatus();
        if (st == QuestionStatus.PUBLISHED || st == QuestionStatus.PENDING) {
            return "已上架/待审核题目需由管理员删除 #" + id;
        }
        questionRepository.delete(q);
        return null;
    }

    public QuestionView toView(Question q) {
        String typeLabel = q.getType() == null ? null : q.getType().getLabel();
        String statusLabel = q.getStatus() == null ? null : q.getStatus().getLabel();
        String sourceLabel = q.getSource() == null ? null : q.getSource().getLabel();
        KnowledgePoint kp = q.getKnowledgePoint();
        return new QuestionView(
                q.getId(),
                q.getType(), typeLabel,
                q.getStatus(), statusLabel,
                q.getSource(), sourceLabel,
                q.getStem(),
                QuestionFormat.optionsFromJson(q.getOptionsJson()),
                q.getAnswer(),
                q.getAnalysis(),
                q.getDifficulty(),
                kp == null ? null : kp.getId(),
                kp == null ? null : kp.getName(),
                kp == null ? null : kp.getCategory(),
                q.getCreatedBy(),
                q.getReviewerName(), q.getReviewComment(),
                q.getSubmittedAt(), q.getPublishedAt(),
                q.getCreatedAt(), q.getUpdatedAt());
    }

    public long countPending() {
        return questionRepository.countByStatus(QuestionStatus.PENDING);
    }
}
