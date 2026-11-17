package com.scf.meta.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerViewRepository extends JpaRepository<ConsumerView, String> {
    List<ConsumerView> findByBuildFactId(String buildFactId);
    List<ConsumerView> findByStatus(String status);
}
