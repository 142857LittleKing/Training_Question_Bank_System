package com.questionbank.service;

import com.questionbank.common.BizException;
import com.questionbank.common.PageResult;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.QuestionDtos.QuestionQuery;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.entity.Question;
import com.questionbank.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 检索使用: 供培训考试/组卷等下游场景调用, 只取"已上架"题目。
 * 该组接口无需登录(见 WebConfig 放行 /api/quiz/**)。
 */
@Service
public class QuizService {

    private final QuestionRepository questionRepository;
    private final QuestionService questionService;

    public QuizService(QuestionRepository questionRepository, QuestionService questionService) {
        this.questionRepository = questionRepository;
        this.questionService = questionService;
    }

    /** 按知识点/题型 随机抽题(已上架) */
    @Transactional(readOnly = true)
    public List<QuestionView> random(QuestionType type, Long kpId, int count) {
        int n = Math.min(Math.max(count, 1), 100);
        List<Question> found = questionRepository.findRandomPublished(
                type == null ? null : type.name(), kpId, n);
        if (found.isEmpty()) {
            throw BizException.notFound("当前条件下没有已上架的题目");
        }
        return found.stream().map(questionService::toView).toList();
    }

    /** 检索已上架题目 */
    @Transactional(readOnly = true)
    public PageResult<QuestionView> searchPublished(String keyword, Long kpId, QuestionType type,
                                                    int page, int size) {
        QuestionQuery q = new QuestionQuery(keyword, kpId, type, QuestionStatus.PUBLISHED, null, null, page, size);
        return questionService.page(q);
    }
}
