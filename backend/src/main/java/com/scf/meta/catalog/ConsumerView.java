package com.scf.meta.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 消费目录中的下游视图：风险视图、资产清单、审计视图。
 * 引用某一构建事实签发版本；被监测器暂停时保留当时身份版本快照。
 */
@Entity
@Table(name = "consumer_view")
public class ConsumerView {

    public enum Type { RISK_VIEW, ASSET_INVENTORY, AUDIT_VIEW }
    public enum Status { ACTIVE, SUSPENDED }

    @Id
    public String id;

    @Column(nullable = false, length = 24)
    public String viewType;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(nullable = false)
    public String buildFactId;

    /** 视图创建时各身份的版本快照 {identityId: versionNo} */
    @Column(columnDefinition = "text")
    public String identityVersions;

    @Column(nullable = false, length = 16)
    public String status = "ACTIVE";

    @Column(columnDefinition = "text")
    public String suspendReason;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
}
