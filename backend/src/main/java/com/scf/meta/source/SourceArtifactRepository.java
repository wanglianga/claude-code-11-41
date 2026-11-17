package com.scf.meta.source;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, String> {
    List<SourceArtifact> findByBuildIdOrderByCollectedAtAsc(String buildId);
    List<SourceArtifact> findByTypeOrderByCollectedAtDesc(String type);
    List<SourceArtifact> findAllByOrderByCollectedAtDesc();
}
