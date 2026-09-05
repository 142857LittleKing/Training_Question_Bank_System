package com.questionbank.repository;

import com.questionbank.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface KnowledgePointRepository
        extends JpaRepository<KnowledgePoint, Long>, JpaSpecificationExecutor<KnowledgePoint> {

    Optional<KnowledgePoint> findByName(String name);

    boolean existsByName(String name);
}
