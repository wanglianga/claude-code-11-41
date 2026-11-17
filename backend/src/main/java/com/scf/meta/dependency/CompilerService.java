package com.scf.meta.dependency;

import com.scf.meta.identity.ComponentClaim;
import com.scf.meta.identity.ComponentClaimRepository;
import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.review.ReviewService;
import com.scf.meta.source.SourceArtifact;
import com.scf.meta.source.SourceArtifactRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 依赖事实编译器：区分被实际解析证据支持的依赖边（PROVEN）
 * 与仅由包仓/扫描推断的候选边（CANDIDATE）；身份冲突或证据不足的边
 * 不得签发为已证实（REJECTED）。
 */
@Service
public class CompilerService {

    private final SourceArtifactRepository sourceRepo;
    private final ComponentClaimRepository claimRepo;
    private final ComponentIdentityRepository identityRepo;
    private final DependencyEdgeRepository edgeRepo;
    private final CompileRunRepository runRepo;
    private final ReviewService reviewService;
    private final String ruleVersion;

    public CompilerService(SourceArtifactRepository sourceRepo,
                           ComponentClaimRepository claimRepo,
                           ComponentIdentityRepository identityRepo,
                           DependencyEdgeRepository edgeRepo,
                           CompileRunRepository runRepo,
                           ReviewService reviewService,
                           @Value("${scf.rule-version}") String ruleVersion) {
        this.sourceRepo = sourceRepo;
        this.claimRepo = claimRepo;
        this.identityRepo = identityRepo;
        this.edgeRepo = edgeRepo;
        this.runRepo = runRepo;
        this.reviewService = reviewService;
        this.ruleVersion = ruleVersion;
    }

    public record CompileResult(CompileRun run, List<DependencyEdge> edges) {}

    /** 编译指定构建在截止点可见证据下的依赖边。cutoff 为 null 表示当前全部可见。 */
    @Transactional
    public CompileResult compile(String buildId, Instant cutoff, String actor) {
        CompileRun run = new CompileRun();
        run.buildId = buildId;
        run.ruleVersion = ruleVersion;
        run.cutoffTime = cutoff;
        runRepo.save(run);

        Instant effectiveCutoff = cutoff == null ? Instant.now().plusSeconds(3650L * 24 * 3600) : cutoff;
        List<SourceArtifact> visibleSources = sourceRepo.findByBuildIdOrderByCollectedAtAsc(buildId)
                .stream().filter(s -> !s.collectedAt.isAfter(effectiveCutoff)).toList();
        Set<String> visibleSourceIds = visibleSources.stream().map(s -> s.id).collect(Collectors.toSet());
        Map<String, String> sourceTypeById = new HashMap<>();
        visibleSources.forEach(s -> sourceTypeById.put(s.id, s.type));

        List<ComponentClaim> claims = claimRepo.findByBuildContext(buildId).stream()
                .filter(c -> visibleSourceIds.contains(c.sourceArtifactId))
                .filter(c -> c.identityId != null).toList();

        // 每个身份聚合支持它的来源
        Map<String, List<ComponentClaim>> claimsByIdentity = claims.stream()
                .collect(Collectors.groupingBy(c -> c.identityId, LinkedHashMap::new, Collectors.toList()));

        edgeRepo.deleteByBuildId(buildId);
        List<DependencyEdge> edges = new ArrayList<>();
        int proven = 0, candidate = 0, rejected = 0;
        for (Map.Entry<String, List<ComponentClaim>> e : claimsByIdentity.entrySet()) {
            ComponentIdentity identity = identityRepo.findById(e.getKey()).orElse(null);
            if (identity == null) continue;
            List<ComponentClaim> own = e.getValue();
            // 实际解析证据（SBOM/锁文件/构建日志）或有效签名均可支持已证实边
            boolean resolvedSupport = own.stream()
                    .anyMatch(c -> ComponentClaim.Kind.RESOLVED.name().equals(c.kind)
                            || ComponentClaim.Kind.SIGNATURE.name().equals(c.kind));
            String supporting = own.stream()
                    .map(c -> sourceTypeById.getOrDefault(c.sourceArtifactId, "?") + "#"
                            + c.sourceArtifactId.substring(0, 8))
                    .distinct().collect(Collectors.joining(", "));

            DependencyEdge edge = new DependencyEdge();
            edge.buildId = buildId;
            edge.fromKey = "build:" + buildId;
            edge.toIdentityId = identity.id;
            edge.toLabel = identity.coordinate()
                    + (identity.publisher == null ? "" : " (" + identity.publisher + ")");
            edge.scope = "runtime";
            edge.compileRunId = run.id;

            String status = identity.status;
            if (ComponentIdentity.Status.CONFIRMED.name().equals(status) && resolvedSupport) {
                edge.edgeStatus = DependencyEdge.Status.PROVEN.name();
                edge.evidenceSummary = "实际解析证据支持: " + supporting;
                proven++;
            } else if (ComponentIdentity.Status.CONFLICT.name().equals(status)) {
                edge.edgeStatus = DependencyEdge.Status.REJECTED.name();
                edge.evidenceSummary = "身份来源冲突，不得签发为已证实: " + identity.conflictReason;
                rejected++;
            } else if (ComponentIdentity.Status.INSUFFICIENT_EVIDENCE.name().equals(status)) {
                edge.edgeStatus = DependencyEdge.Status.REJECTED.name();
                edge.evidenceSummary = "身份证据不足（唯一依据已撤销），不得签发为已证实";
                rejected++;
            } else {
                edge.edgeStatus = DependencyEdge.Status.CANDIDATE.name();
                edge.evidenceSummary = "仅推断证据（包仓/扫描），作为候选边: " + supporting;
                candidate++;
            }
            edgeRepo.save(edge);
            edges.add(edge);
        }
        run.finishedAt = Instant.now();
        run.summary = "proven=" + proven + " candidate=" + candidate + " rejected=" + rejected
                + " sources=" + visibleSources.size();
        runRepo.save(run);
        reviewService.record("EDGE", buildId, "COMPILED",
                "run=" + run.id.substring(0, 8) + " " + run.summary, actor);
        return new CompileResult(run, edges);
    }
}
