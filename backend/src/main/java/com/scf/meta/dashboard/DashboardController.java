package com.scf.meta.dashboard;

import com.scf.meta.buildfact.BuildFactRepository;
import com.scf.meta.catalog.ConsumerViewRepository;
import com.scf.meta.common.ApiResponse;
import com.scf.meta.dependency.DependencyEdgeRepository;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.monitor.MonitorEventRepository;
import com.scf.meta.source.SourceArtifact;
import com.scf.meta.source.SourceArtifactRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SourceArtifactRepository sourceRepo;
    private final ComponentIdentityRepository identityRepo;
    private final DependencyEdgeRepository edgeRepo;
    private final BuildFactRepository factRepo;
    private final ConsumerViewRepository viewRepo;
    private final MonitorEventRepository monitorRepo;

    public DashboardController(SourceArtifactRepository sourceRepo,
                               ComponentIdentityRepository identityRepo,
                               DependencyEdgeRepository edgeRepo,
                               BuildFactRepository factRepo,
                               ConsumerViewRepository viewRepo,
                               MonitorEventRepository monitorRepo) {
        this.sourceRepo = sourceRepo;
        this.identityRepo = identityRepo;
        this.edgeRepo = edgeRepo;
        this.factRepo = factRepo;
        this.viewRepo = viewRepo;
        this.monitorRepo = monitorRepo;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sources", sourceRepo.count());
        m.put("identities", identityRepo.count());
        m.put("identitiesConfirmed", identityRepo.findByStatus("CONFIRMED").size());
        m.put("identitiesCandidate", identityRepo.findByStatus("CANDIDATE").size());
        m.put("identitiesConflict", identityRepo.findByStatus("CONFLICT").size());
        m.put("identitiesInsufficient", identityRepo.findByStatus("INSUFFICIENT_EVIDENCE").size());
        m.put("edges", edgeRepo.count());
        m.put("buildFacts", factRepo.count());
        m.put("views", viewRepo.count());
        m.put("viewsSuspended", viewRepo.findByStatus("SUSPENDED").size());
        m.put("monitorEvents", monitorRepo.count());
        return ApiResponse.ok(m);
    }

    /** 全部构建标识（供前端下拉选择）。 */
    @GetMapping("/builds")
    public ApiResponse<List<String>> builds() {
        Set<String> ids = new TreeSet<>();
        for (SourceArtifact s : sourceRepo.findAll()) {
            if (s.buildId != null && !s.buildId.isBlank()) ids.add(s.buildId);
        }
        return ApiResponse.ok(List.copyOf(ids));
    }
}
