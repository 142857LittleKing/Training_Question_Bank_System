package com.questionbank.repository;

import com.questionbank.entity.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReviewRecordRepository
        extends JpaRepository<ReviewRecord, Long>, JpaSpecificationExecutor<ReviewRecord> {

    List<ReviewRecord> findByQuestionIdOrderByCreatedAtDesc(Long questionId);
}
