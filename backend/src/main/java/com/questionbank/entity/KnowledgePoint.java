package com.questionbank.entity;

import jakarta.persistence.*;

/** 知识点 */
@Entity
@Table(name = "knowledge_point",
        uniqueConstraints = @UniqueConstraint(name = "uk_kp_name", columnNames = "name"))
public class KnowledgePoint extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    public KnowledgePoint() {
    }

    public KnowledgePoint(String name, String category, String description, String createdBy) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
