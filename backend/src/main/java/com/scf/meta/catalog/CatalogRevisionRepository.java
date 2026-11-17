package com.scf.meta.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRevisionRepository extends JpaRepository<CatalogRevision, String> {
    List<CatalogRevision> findByCatalogEntryIdOrderByRevisionDesc(String catalogEntryId);
}
