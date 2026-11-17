package com.scf.meta.buildfact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 构建事实：在截止点冻结关系、规则与来源对象后签发的不可变事实。
 * 签发后不再改写；截止点之后到达的来源登记为后续差异来源。
 */
@Entity
@Table(name = "build_fact")
public class BuildFact {

    @Id
    public String id;

    @Column(nullable = false, length = 128)
    public String buildId;

    /** 同一构建的签发版本，自 1 递增 */
    @Column(nullable = false)
    public int factVersion;

    @Column(nullable = false)
    public Instant cutoffTime;

    @Column(nullable = false)
    public Instant issuedAt;

    @Column(nullable = false, length = 64)
    public String ruleVersion;

    @Column(nullable = false, length = 16)
    public String status = "ISSUED";

    /** 冻结时可见来源对象 [{id,type,hash,collectedAt}] */
    @Column(columnDefinition = "text")
    public String sourceHashes;

    /** 截止点后到达、登记为后续差异的来源 [{id,type,hash,collectedAt,note}] */
    @Column(columnDefinition = "text")
    public String postCutoffSources;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (issuedAt == null) issuedAt = Instant.now();
    }
}
