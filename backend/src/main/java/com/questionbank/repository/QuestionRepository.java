package com.questionbank.repository;

import com.questionbank.common.enums.QuestionStatus;
import com.questionbank.common.enums.QuestionType;
import com.questionbank.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository
        extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    Optional<Question> findFirstByStem(String stem);

    long countByStatus(QuestionStatus status);

    long countByType(QuestionType type);

    long countBySource(com.questionbank.common.enums.QuestionSource source);

    @Query("select q.status, count(q) from Question q group by q.status")
    List<Object[]> countGroupByStatus();

    @Query("select q.type, count(q) from Question q group by q.type")
    List<Object[]> countGroupByType();

    @Query("select q.source, count(q) from Question q group by q.source")
    List<Object[]> countGroupBySource();

    /** 待审核数量(不含已上架), 用于审核中心红点 */
    @Query("select count(q) from Question q where q.status = :status")
    long countByStatusNamed(@Param("status") QuestionStatus status);

    @Query("select count(q) from Question q where q.knowledgePoint.id = :kpId")
    long countByKnowledgePointId(@Param("kpId") Long kpId);

    /** 已上架题目随机抽题 */
    @Query(value = "select * from question " +
            "where status = 'PUBLISHED' " +
            "and (:type is null or type = :type) " +
            "and (:kpId is null or knowledge_point_id = :kpId) " +
            "order by rand() limit :count", nativeQuery = true)
    List<Question> findRandomPublished(@Param("type") String type,
                                       @Param("kpId") Long kpId,
                                       @Param("count") int count);

    List<Question> findTop10ByOrderByCreatedAtDesc();
}
