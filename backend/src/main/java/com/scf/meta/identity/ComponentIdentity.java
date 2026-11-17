package com.scf.meta.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 组件身份：由若干声明归并而成。状态机：
 * CANDIDATE（候选）→ CONFIRMED（已确认）；
 * 来源间哈希冲突 → CONFLICT；唯一依据被撤销 → INSUFFICIENT_EVIDENCE。
 * versionNo 在每次状态变化时递增，供历史视图冻结当时身份版本。
 */
@Entity
@Table(name = "component_identity")
public class ComponentIdentity {

    public enum Status { CANDIDATE, CONFIRMED, CONFLICT, INSUFFICIENT_EVIDENCE }

    @Id
    public String id;

    /** ecosystem|namespace|name|version|publisher|hash */
    @Column(nullable = false, unique = true, length = 1408)
    public String identityKey;

    @Column(length = 32)
    public String ecosystem;

    @Column(length = 255)
    public String namespace;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(length = 255)
    public String version;

    @Column(length = 255)
    public String publisher;

    @Column(length = 16)
    public String hashAlg;

    @Column(length = 128)
    public String hashValue;

    @Column(nullable = false, length = 24)
    public String status;

    @Column(nullable = false)
    public int versionNo = 1;

    @Column(columnDefinition = "text")
    public String conflictReason;

    /** 该身份确认所依赖的签名记录（若有） */
    public String signatureId;

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

    public static String keyOf(String ecosystem, String namespace, String name,
                               String version, String publisher, String hashValue) {
        return String.join("|",
                ecosystem == null || ecosystem.isBlank() ? "unknown" : ecosystem,
                namespace == null ? "" : namespace,
                name == null ? "" : name,
                version == null ? "" : version,
                publisher == null ? "" : publisher,
                hashValue == null ? "" : hashValue);
    }

    public String coordinate() {
        return (ecosystem == null ? "unknown" : ecosystem) + ":"
                + (namespace == null || namespace.isBlank() ? "" : namespace + "/")
                + name + "@" + (version == null ? "?" : version);
    }
}
