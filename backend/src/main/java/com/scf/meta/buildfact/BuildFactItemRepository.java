package com.scf.meta.buildfact;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildFactItemRepository extends JpaRepository<BuildFactItem, String> {
    List<BuildFactItem> findByBuildFactId(String buildFactId);
    List<BuildFactItem> findByItemKindAndRefId(String itemKind, String refId);
}
