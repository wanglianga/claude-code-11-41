package com.scf.meta.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 依赖边：构建（或根组件）到组件的依赖关系。
 * PROVEN 表示被构建实际解析证据支持；CANDIDATE 表示仅由包仓/扫描推断；
 * REJECTED 表示因身份冲突或证据不足而不可签发为已证实。
 */
@Entity
@Table(name = "dependency_edge")
public class DependencyEdge {

    public enum Status { PROVEN, CANDIDATE, REJECTED }

    @Id
    public String id;

    @Column(nullable = false, length = 128)
    public String buildId;

    /** 依赖源（根为 build:<buildId>，否则为组件坐标） */
    @Column(nullable = false, length = 1408)
    public String fromKey;

    /** 目标身份（已归并） */
    public String toIdentityId;

    /** 目标坐标展示文本 */
    @Column(nullable = false, length = 1408)
    public String toLabel;

    @Column(length = 32)
    public String scope;

    @Column(nullable = false, length = 16)
    public String edgeStatus;

    /** 证据摘要（哪些来源支持/为何只是候选） */
    @Column(columnDefinition = "text")
    public String evidenceSummary;

    public String compileRunId;

    @Column(nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
