package com.scf.meta.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 复审事件：身份、依赖边、构建事实、下游视图的全部状态变迁审计。 */
@Entity
@Table(name = "review_event")
public class ReviewEvent {

    @Id
    public String id;

    /** IDENTITY / EDGE / BUILD_FACT / VIEW / SOURCE */
    @Column(nullable = false, length = 32)
    public String subjectType;

    @Column(nullable = false, length = 64)
    public String subjectId;

    @Column(nullable = false, length = 64)
    public String action;

    @Column(columnDefinition = "text")
    public String detail;

    @Column(length = 64)
    public String actor;

    @Column(nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
