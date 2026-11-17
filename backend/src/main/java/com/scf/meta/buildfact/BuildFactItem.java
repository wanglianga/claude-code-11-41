package com.scf.meta.buildfact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

/** 构建事实冻结条目：身份或依赖边在签发时刻的快照。 */
@Entity
@Table(name = "build_fact_item")
public class BuildFactItem {

    public enum Kind { IDENTITY, EDGE }

    @Id
    public String id;

    @Column(nullable = false)
    public String buildFactId;

    @Column(nullable = false, length = 24)
    public String itemKind;

    /** 被冻结对象 id（身份或依赖边） */
    @Column(nullable = false)
    public String refId;

    /** 冻结快照（JSON），签发后不再变化 */
    @Column(nullable = false, columnDefinition = "text")
    public String snapshot;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
