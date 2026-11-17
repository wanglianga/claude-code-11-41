package com.scf.meta.buildfact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildFactRepository extends JpaRepository<BuildFact, String> {
    List<BuildFact> findByBuildIdOrderByFactVersionDesc(String buildId);
    Optional<BuildFact> findTopByBuildIdOrderByFactVersionDesc(String buildId);
}
