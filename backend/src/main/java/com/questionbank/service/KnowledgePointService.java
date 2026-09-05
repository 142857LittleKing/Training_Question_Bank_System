package com.questionbank.service;

import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.PageResult;
import com.questionbank.dto.KnowledgePointDtos.KnowledgePointReq;
import com.questionbank.dto.KnowledgePointDtos.KnowledgePointView;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.repository.KnowledgePointRepository;
import com.questionbank.repository.QuestionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class KnowledgePointService {

    private final KnowledgePointRepository kpRepository;
    private final QuestionRepository questionRepository;

    public KnowledgePointService(KnowledgePointRepository kpRepository, QuestionRepository questionRepository) {
        this.kpRepository = kpRepository;
        this.questionRepository = questionRepository;
    }

    /** 全部知识点(下拉选择用) */
    @Transactional(readOnly = true)
    public List<KnowledgePoint> listAll() {
        return kpRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgePointView> page(String keyword, int page, int size) {
        Specification<KnowledgePoint> spec = (root, q, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim() + "%";
            return cb.or(cb.like(root.get("name"), like), cb.like(root.get("category"), like));
        };
        Page<KnowledgePoint> result = kpRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<KnowledgePointView> views = result.getContent().stream().map(this::toView).toList();
        return PageResult.of(views, result.getTotalElements(), page, size);
    }

    public KnowledgePointView toView(KnowledgePoint kp) {
        return new KnowledgePointView(kp.getId(), kp.getName(), kp.getCategory(), kp.getDescription(),
                kp.getCreatedBy(), questionRepository.countByKnowledgePointId(kp.getId()), kp.getCreatedAt());
    }

    @Transactional
    public KnowledgePoint create(KnowledgePointReq req) {
        if (kpRepository.existsByName(req.name().trim())) {
            throw BizException.bad("知识点名称已存在: " + req.name().trim());
        }
        KnowledgePoint kp = new KnowledgePoint(req.name().trim(), blankToNull(req.category()),
                blankToNull(req.description()), UserContext.required().getName());
        return kpRepository.save(kp);
    }

    @Transactional
    public KnowledgePoint update(Long id, KnowledgePointReq req) {
        KnowledgePoint kp = getRequired(id);
        String name = req.name().trim();
        if (!kp.getName().equals(name) && kpRepository.existsByName(name)) {
            throw BizException.bad("知识点名称已存在: " + name);
        }
        kp.setName(name);
        kp.setCategory(blankToNull(req.category()));
        kp.setDescription(blankToNull(req.description()));
        return kpRepository.save(kp);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgePoint kp = getRequired(id);
        long used = questionRepository.countByKnowledgePointId(id);
        if (used > 0) {
            throw BizException.bad("该知识点下还有 " + used + " 道题目, 不能删除(可先转移/删除题目)");
        }
        kpRepository.delete(kp);
    }

    public KnowledgePoint getRequired(Long id) {
        return kpRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("知识点不存在: id=" + id));
    }

    /** 按名称查询, 不存在则创建(用于导入/AI 生成时自动挂接知识点) */
    @Transactional
    public KnowledgePoint getOrCreateByName(String name, String category) {
        if (!StringUtils.hasText(name)) {
            throw BizException.bad("知识点名称不能为空");
        }
        Optional<KnowledgePoint> exist = kpRepository.findByName(name.trim());
        return exist.orElseGet(() -> kpRepository.save(new KnowledgePoint(name.trim(),
                category, null, UserContext.required().getName())));
    }

    @Transactional(readOnly = true)
    public KnowledgePoint getByNameOrNull(String name) {
        return StringUtils.hasText(name) ? kpRepository.findByName(name.trim()).orElse(null) : null;
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
