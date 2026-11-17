package com.scf.meta.monitor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorEventRepository extends JpaRepository<MonitorEvent, String> {
    List<MonitorEvent> findAllByOrderByCreatedAtDesc();
    List<MonitorEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
