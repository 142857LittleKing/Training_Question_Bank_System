package com.questionbank.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.questionbank.common.JsonUtils;
import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.common.enums.ReviewAction;
import com.questionbank.common.enums.UserRole;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.entity.Question;
import com.questionbank.entity.ReviewRecord;
import com.questionbank.entity.SysUser;
import com.questionbank.repository.KnowledgePointRepository;
import com.questionbank.repository.QuestionRepository;
import com.questionbank.repository.ReviewRecordRepository;
import com.questionbank.repository.SysUserRepository;
import com.questionbank.service.QuestionFormat;
import com.questionbank.dto.QuestionDtos.QuestionPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动初始化:
 *  1) 默认账号(admin/generator/reviewer)
 *  2) 空库时自动导入内置题库 seed/dangshi_150.json(党建知识竞赛 150 题), 直接上架
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserRepository userRepository;
    private final KnowledgePointRepository kpRepository;
    private final QuestionRepository questionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResourceLoader resourceLoader;

    @Value("${seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${seed.data-file:}")
    private String seedDataFile;

    public DataInitializer(SysUserRepository userRepository,
                           KnowledgePointRepository kpRepository,
                           QuestionRepository questionRepository,
                           ReviewRecordRepository reviewRecordRepository,
                           PasswordEncoder passwordEncoder,
                           ResourceLoader resourceLoader) {
        this.userRepository = userRepository;
        this.kpRepository = kpRepository;
        this.questionRepository = questionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.resourceLoader = resourceLoader;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedQuestions();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("[init] 创建默认账号");
        userRepository.saveAll(List.of(
                new SysUser("admin", passwordEncoder.encode("admin123"), "系统管理员", UserRole.ADMIN),
                new SysUser("generator", passwordEncoder.encode("gen123456"), "录入出题员", UserRole.GENERATOR),
                new SysUser("reviewer", passwordEncoder.encode("rev123456"), "题目审核员", UserRole.REVIEWER)
        ));
    }

    private void seedQuestions() {
        if (!seedEnabled || seedDataFile == null || seedDataFile.isBlank()) {
            return;
        }
        if (questionRepository.count() > 0) {
            return;
        }
        try {
            Resource res = resourceLoader.getResource(seedDataFile);
            if (!res.exists()) {
                log.warn("[init] seed 数据文件不存在: {}", seedDataFile);
                return;
            }
            String json = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = JsonUtils.mapper().readTree(json);
            if (!root.isArray()) {
                log.warn("[init] seed 文件格式错误(应为 JSON 数组)");
                return;
            }
            int imported = 0;
            int skipped = 0;
            for (JsonNode n : root) {
                QuestionPayload raw = mapSeed(n);
                if (raw == null) {
                    skipped++;
                    continue;
                }
                QuestionPayload p = QuestionFormat.normalize(raw);
                List<String> reasons = QuestionFormat.validate(p);
                if (!reasons.isEmpty()) {
                    skipped++;
                    log.warn("[init] 跳过不合规题目: {} reasons={}", safeStem(p.stem()), reasons);
                    continue;
                }
                KnowledgePoint kp = kpRepository.findByName(p.knowledgePointName())
                        .orElseGet(() -> kpRepository.save(new KnowledgePoint(p.knowledgePointName(),
                                "党建培训", "党建知识竞赛题库(内置演示数据)", "system")));
                Question q = new Question();
                q.setKnowledgePoint(kp);
                q.setType(p.type());
                q.setStem(p.stem());
                q.setOptionsJson(QuestionFormat.optionsToJson(p.options()));
                q.setAnswer(p.answer());
                q.setAnalysis(p.analysis());
                q.setDifficulty(p.difficulty());
                q.setSource(QuestionSource.IMPORT);
                q.setStatus(QuestionStatus.PUBLISHED);
                q.setCreatedBy("system");
                q.setPublishedAt(LocalDateTime.now());
                q = questionRepository.save(q);
                reviewRecordRepository.save(new ReviewRecord(q.getId(), safeStem(q.getStem()), null,
                        "system", ReviewAction.IMPORT_PASS, "内置题库初始化导入"));
                imported++;
            }
            log.info("[init] 题库初始化完成: 导入 {} 题, 跳过 {} 题", imported, skipped);
        } catch (Exception e) {
            log.error("[init] 题库初始化失败", e);
        }
    }

    private String safeStem(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    private QuestionPayload mapSeed(JsonNode n) {
        QuestionType type = QuestionType.parse(text(n, "type"));
        String stem = text(n, "stem");
        String answer = text(n, "answer");
        String analysis = text(n, "analysis");
        String kp = text(n, "knowledgePointName") != null ? text(n, "knowledgePointName") : text(n, "knowledgePoint");
        JsonNode opts = n.get("options");
        List<String> options = null;
        if (opts != null && opts.isArray()) {
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            opts.forEach(o -> list.add(o.isTextual() ? o.asText() : o.toString()));
            options = list;
        }
        return new QuestionPayload(type, stem, options, answer, analysis, 3, null, kp);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || !v.isTextual() ? null : v.asText();
    }
}
