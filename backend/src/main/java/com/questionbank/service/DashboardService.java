package com.questionbank.service;

import com.questionbank.common.enums.QuestionSource;
import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.dto.DashboardDtos.DashboardSummary;
import com.questionbank.dto.DashboardDtos.KpCount;
import com.questionbank.dto.DashboardDtos.NameCount;
import com.questionbank.dto.QuestionDtos.QuestionView;
import com.questionbank.entity.KnowledgePoint;
import com.questionbank.entity.Question;
import com.questionbank.repository.KnowledgePointRepository;
import com.questionbank.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository kpRepository;
    private final QuestionService questionService;

    public DashboardService(QuestionRepository questionRepository,
                            KnowledgePointRepository kpRepository,
                            QuestionService questionService) {
        this.questionRepository = questionRepository;
        this.kpRepository = kpRepository;
        this.questionService = questionService;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        List<NameCount> byStatus = countByEnum(
                List.of(QuestionStatus.values()),
                listToMap(questionRepository.countGroupByStatus()),
                s -> ((QuestionStatus) s).getLabel());
        List<NameCount> byType = countByEnum(
                List.of(QuestionType.values()),
                listToMap(questionRepository.countGroupByType()),
                t -> ((QuestionType) t).getLabel());
        List<NameCount> bySource = countByEnum(
                List.of(QuestionSource.values()),
                listToMap(questionRepository.countGroupBySource()),
                s -> ((QuestionSource) s).getLabel());

        List<KpCount> topKps = new ArrayList<>();
        for (KnowledgePoint kp : kpRepository.findAll()) {
            long n = questionRepository.countByKnowledgePointId(kp.getId());
            if (n > 0) {
                topKps.add(new KpCount(kp.getId(), kp.getName(), n));
            }
        }
        topKps.sort(Comparator.comparingLong(KpCount::count).reversed());
        if (topKps.size() > 5) {
            topKps = new ArrayList<>(topKps.subList(0, 5));
        }

        List<QuestionView> recent = questionRepository.findTop10ByOrderByCreatedAtDesc()
                .stream().map(questionService::toView).toList();

        return new DashboardSummary(
                questionRepository.count(),
                questionRepository.countByStatus(QuestionStatus.PUBLISHED),
                questionRepository.countByStatus(QuestionStatus.PENDING),
                byStatus, byType, bySource, topKps, recent);
    }

    private Map<String, Long> listToMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private <E extends Enum<E>> List<NameCount> countByEnum(List<E> enums, Map<String, Long> countMap,
                                                            java.util.function.Function<E, String> labelFn) {
        List<NameCount> out = new ArrayList<>();
        for (E e : enums) {
            out.add(new NameCount(e.name(), labelFn.apply(e), countMap.getOrDefault(e.name(), 0L)));
        }
        return out;
    }
}
