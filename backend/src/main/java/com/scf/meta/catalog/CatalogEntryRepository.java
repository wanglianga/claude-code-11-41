package com.scf.meta.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, String> {
    Optional<CatalogEntry> findByEcosystemAndNamespaceAndName(String ecosystem, String namespace, String name);
    List<CatalogEntry> findByName(String name);
}
