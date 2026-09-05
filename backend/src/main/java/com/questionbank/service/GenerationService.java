package com.questionbank.service;

import com.questionbank.ai.AiProvider;
import com.questionbank.ai.AiProviderFactory;
import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.common.enums.UserRole;
import com.questionbank.dto.AiDtos.GenerateReq;
import com.questionbank.dto.AiDtos.GenerationItemView;
import com.questionbank.dto.AiDtos.GenerationResult;
import com.questionbank.dto.AiDtos.SaveGeneratedReq;
import com.questionbank.dto.AiDtos.SaveGeneratedResult;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.entity.Question;
import com.questionbank.repository.QuestionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 按知识点自动出题: 组装出题任务 -> 调用出题源 -> 逐题格式校验 -> (前端确认后)落库为 GENERATED。
 */
@Service
public class GenerationService {

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AiProviderFactory providerFactory;
    private final QuestionRepository questionRepository;
    private final KnowledgePointService kpService;

    public GenerationService(AiProviderFactory providerFactory,
                             QuestionRepository questionRepository,
                             KnowledgePointService kpService) {
        this.providerFactory = providerFactory;
        this.questionRepository = questionRepository;
        this.kpService = kpService;
    }

    /** 生成题目(不落库), 逐题给出格式校验结果 */
    public GenerationResult generate(GenerateReq req) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        KnowledgePoint kp = kpService.getRequired(req.knowledgePointId());
        AiProvider provider = providerFactory.get();

        // 确定各题型数量(type=null -> 四种题型均衡配比)
        List<RequestGroup> groups = planGroups(req);
        List<QuestionPayload> drafts = new ArrayList<>();
        for (RequestGroup g : groups) {
            List<QuestionPayload> part;
            try {
                part = provider.generate(new AiProvider.GenRequest(
                        kp, g.type(), g.count(), req.difficulty() == null ? 3 : req.difficulty(),
                        req.extraInstruction()));
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw BizException.bad("出题失败(" + provider.label() + "): " + e.getMessage());
            }
            if (part.size() > g.count()) {
                part = part.subList(0, g.count());
            }
            // 规整: 补上请求题型 / 难度
            for (QuestionPayload p : part) {
                QuestionPayload fixed = new QuestionPayload(
                        g.type() != null ? g.type() : p.type(),
                        p.stem(), p.options(), p.answer(), p.analysis(),
                        req.difficulty() == null ? p.difficulty() : req.difficulty(),
                        kp.getId(), kp.getName());
                drafts.add(QuestionFormat.normalize(fixed));
            }
        }

        // 逐题格式校验 + 查重
        List<GenerationItemView> items = new ArrayList<>();
        int validCount = 0;
        int index = 0;
        for (QuestionPayload p : drafts) {
            List<String> reasons = new ArrayList<>(QuestionFormat.validate(p));
            boolean duplicate = questionRepository.findFirstByStem(p.stem()).isPresent();
            if (duplicate) {
                reasons.add("与库中已有题目题干重复(建议在\"批量导入/生成\"场景跳过)");
            }
            boolean valid = reasons.isEmpty();
            if (valid) {
                validCount++;
            }
            items.add(new GenerationItemView(index++, valid, reasons, duplicate, p));
        }

        return new GenerationResult(provider.name(), provider.label(),
                providerFactory.modeTip(), req.count(), validCount, items);
    }

    private record RequestGroup(QuestionType type, int count) {
    }

    private List<RequestGroup> planGroups(GenerateReq req) {
        List<RequestGroup> groups = new ArrayList<>();
        if (req.type() != null) {
            groups.add(new RequestGroup(req.type(), req.count()));
            return groups;
        }
        QuestionType[] all = QuestionType.values();
        int remaining = req.count();
        int per = (int) Math.ceil((double) req.count() / all.length);
        for (QuestionType t : all) {
            int n = Math.min(per, remaining);
            if (n > 0) {
                groups.add(new RequestGroup(t, n));
                remaining -= n;
            }
        }
        return groups;
    }

    /** 保存通过校验的生成题(status=GENERATED, 待提交审核) */
    public SaveGeneratedResult save(SaveGeneratedReq req) {
        UserContext.requireRole(UserRole.GENERATOR, UserRole.ADMIN);
        if (req.questions() == null || req.questions().isEmpty()) {
            throw BizException.bad("没有可保存的题目");
        }
        String batch = req.batchLabel() != null && !req.batchLabel().isBlank()
                ? req.batchLabel().trim()
                : "AI-" + LocalDateTime.now().format(BATCH_FMT);

        int saved = 0;
        int skippedInvalid = 0;
        int skippedDuplicate = 0;
        for (QuestionPayload raw : req.questions()) {
            QuestionPayload p = QuestionFormat.normalize(raw);
            List<String> reasons = QuestionFormat.validate(p);
            if (!reasons.isEmpty()) {
                skippedInvalid++;
                continue;
            }
            if (questionRepository.findFirstByStem(p.stem()).isPresent()) {
                skippedDuplicate++;
                continue;
            }
            Question q = new Question();
            apply(q, p, batch);
            questionRepository.save(q);
            saved++;
        }
        return new SaveGeneratedResult(req.questions().size(), saved, skippedInvalid, skippedDuplicate, batch);
    }

    private void apply(Question q, QuestionPayload p, String batch) {
        KnowledgePoint kp;
        if (p.knowledgePointId() != null) {
            kp = kpService.getRequired(p.knowledgePointId());
        } else {
            kp = kpService.getOrCreateByName(p.knowledgePointName(), "培训");
        }
        q.setKnowledgePoint(kp);
        q.setType(p.type());
        q.setStem(p.stem());
        q.setOptionsJson(QuestionFormat.optionsToJson(p.options()));
        q.setAnswer(p.answer());
        q.setAnalysis(p.analysis() == null || p.analysis().isBlank() ? null : p.analysis());
        q.setDifficulty(p.difficulty());
        q.setSource(QuestionSource.AI);
        q.setStatus(QuestionStatus.GENERATED);
        q.setGenerateBatch(batch);
        q.setCreatedBy(UserContext.required().getName());
    }
}
