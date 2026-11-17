package com.scf.meta.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentIdentityRepository extends JpaRepository<ComponentIdentity, String> {
    Optional<ComponentIdentity> findByIdentityKey(String identityKey);
    List<ComponentIdentity> findByStatus(String status);
    List<ComponentIdentity> findByEcosystemAndNamespaceAndName(String ecosystem, String namespace, String name);
    List<ComponentIdentity> findByName(String name);
    List<ComponentIdentity> findBySignatureId(String signatureId);
}
