package com.scf.meta.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 组件目录条目：可被后续修订（别名、发布者归属），修订不改写历史事实。 */
@Entity
@Table(name = "catalog_entry")
public class CatalogEntry {

    @Id
    public String id;

    @Column(length = 32)
    public String ecosystem;

    @Column(length = 255)
    public String namespace;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(length = 255)
    public String publisher;

    /** 别名列表（JSON 数组） */
    @Column(columnDefinition = "text")
    public String aliases;

    @Column(nullable = false)
    public int revision = 1;

    @Column(nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (updatedAt == null) updatedAt = Instant.now();
    }
}
