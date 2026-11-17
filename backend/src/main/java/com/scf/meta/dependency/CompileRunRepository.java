package com.scf.meta.dependency;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompileRunRepository extends JpaRepository<CompileRun, String> {
    List<CompileRun> findByBuildIdOrderByStartedAtDesc(String buildId);
}
