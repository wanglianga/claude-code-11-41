package com.scf.meta.dependency;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependencyEdgeRepository extends JpaRepository<DependencyEdge, String> {
    List<DependencyEdge> findByBuildId(String buildId);
    List<DependencyEdge> findByBuildIdAndEdgeStatus(String buildId, String edgeStatus);
    List<DependencyEdge> findByToIdentityId(String toIdentityId);
    void deleteByBuildId(String buildId);
}
