package com.scf.meta.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 变动监测事件：目录修订、签名撤销、截止点后补传等。 */
@Entity
@Table(name = "monitor_event")
public class MonitorEvent {

    public enum Type { CATALOG_REVISION, SIGNATURE_REVOKED, LATE_SOURCE }

    @Id
    public String id;

    @Column(nullable = false, length = 32)
    public String eventType;

    @Column(columnDefinition = "text")
    public String detail;

    /** 受影响的下游视图 id 列表（JSON 数组） */
    @Column(columnDefinition = "text")
    public String affectedViews;

    @Column(nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
