package com.scf.meta.identity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentClaimRepository extends JpaRepository<ComponentClaim, String> {
    List<ComponentClaim> findBySourceArtifactId(String sourceArtifactId);
    List<ComponentClaim> findByIdentityId(String identityId);
    List<ComponentClaim> findByBuildContext(String buildContext);
    List<ComponentClaim> findByName(String name);
}
