package com.scf.meta.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 一次依赖事实编译运行记录。 */
@Entity
@Table(name = "compile_run")
public class CompileRun {

    @Id
    public String id;

    @Column(nullable = false, length = 128)
    public String buildId;

    @Column(nullable = false, length = 64)
    public String ruleVersion;

    /** 编译使用的证据截止点（签发时与事实截止点一致） */
    public Instant cutoffTime;

    @Column(nullable = false)
    public Instant startedAt;

    public Instant finishedAt;

    @Column(columnDefinition = "text")
    public String summary;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (startedAt == null) startedAt = Instant.now();
    }
}
