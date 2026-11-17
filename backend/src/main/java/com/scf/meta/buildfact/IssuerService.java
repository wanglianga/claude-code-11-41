package com.scf.meta.buildfact;

import com.scf.meta.common.Json;
import com.scf.meta.dependency.CompilerService;
import com.scf.meta.dependency.DependencyEdge;
import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.monitor.MonitorEvent;
import com.scf.meta.monitor.MonitorEventRepository;
import com.scf.meta.review.ReviewService;
import com.scf.meta.source.SourceArtifact;
import com.scf.meta.source.SourceArtifactRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 构建事实签发器：在截止点冻结关系、规则与来源对象，签发不可变构建事实。
 * 截止点之后到达的来源登记为后续差异来源，不改写已签发事实。
 */
@Service
public class IssuerService {

    private final BuildFactRepository factRepo;
    private final BuildFactItemRepository itemRepo;
    private final SourceArtifactRepository sourceRepo;
    private final ComponentIdentityRepository identityRepo;
    private final CompilerService compilerService;
    private final MonitorEventRepository monitorRepo;
    private final ReviewService reviewService;
    private final Json json;
    private final String ruleVersion;

    public IssuerService(BuildFactRepository factRepo,
                         BuildFactItemRepository itemRepo,
                         SourceArtifactRepository sourceRepo,
                         ComponentIdentityRepository identityRepo,
                         CompilerService compilerService,
                         MonitorEventRepository monitorRepo,
                         ReviewService reviewService,
                         Json json,
                         @Value("${scf.rule-version}") String ruleVersion) {
        this.factRepo = factRepo;
        this.itemRepo = itemRepo;
        this.sourceRepo = sourceRepo;
        this.identityRepo = identityRepo;
        this.compilerService = compilerService;
        this.monitorRepo = monitorRepo;
        this.reviewService = reviewService;
        this.json = json;
        this.ruleVersion = ruleVersion;
    }

    @Transactional
    public BuildFact issue(String buildId, Instant cutoff, String actor) {
        Instant effectiveCutoff = cutoff == null ? Instant.now() : cutoff;
        List<SourceArtifact> all = sourceRepo.findByBuildIdOrderByCollectedAtAsc(buildId);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("构建 " + buildId + " 没有任何来源，无法签发");
        }
        List<SourceArtifact> visible = all.stream()
                .filter(s -> !s.collectedAt.isAfter(effectiveCutoff)).toList();
        List<SourceArtifact> late = all.stream()
                .filter(s -> s.collectedAt.isAfter(effectiveCutoff)).toList();

        // 用截止点可见证据编译依赖边
        CompilerService.CompileResult compiled = compilerService.compile(buildId, effectiveCutoff, actor);

        BuildFact fact = new BuildFact();
        fact.buildId = buildId;
        fact.cutoffTime = effectiveCutoff;
        fact.ruleVersion = ruleVersion;
        fact.factVersion = factRepo.findTopByBuildIdOrderByFactVersionDesc(buildId)
                .map(f -> f.factVersion + 1).orElse(1);
        fact.sourceHashes = json.write(visible.stream().map(this::sourceRef).toList());
        fact.postCutoffSources = json.write(late.stream().map(s -> {
            Map<String, Object> ref = new LinkedHashMap<>(sourceRef(s));
            ref.put("note", "截止点后到达，登记为后续差异来源，不改写本事实");
            return ref;
        }).toList());
        factRepo.save(fact);

        // 冻结身份与依赖边快照
        List<ComponentIdentity> identities = new ArrayList<>();
        for (DependencyEdge edge : compiled.edges()) {
            ComponentIdentity identity = identityRepo.findById(edge.toIdentityId).orElse(null);
            if (identity != null && identities.stream().noneMatch(i -> i.id.equals(identity.id))) {
                identities.add(identity);
            }
        }
        for (ComponentIdentity identity : identities) {
            BuildFactItem item = new BuildFactItem();
            item.buildFactId = fact.id;
            item.itemKind = BuildFactItem.Kind.IDENTITY.name();
            item.refId = identity.id;
            item.snapshot = json.write(identitySnapshot(identity));
            itemRepo.save(item);
        }
        for (DependencyEdge edge : compiled.edges()) {
            BuildFactItem item = new BuildFactItem();
            item.buildFactId = fact.id;
            item.itemKind = BuildFactItem.Kind.EDGE.name();
            item.refId = edge.id;
            item.snapshot = json.write(edgeSnapshot(edge));
            itemRepo.save(item);
        }
        reviewService.record("BUILD_FACT", fact.id, "ISSUED",
                "build=" + buildId + " v" + fact.factVersion + " cutoff=" + effectiveCutoff
                        + " identities=" + identities.size() + " edges=" + compiled.edges().size()
                        + " lateSources=" + late.size(), actor);
        if (!late.isEmpty()) {
            MonitorEvent event = new MonitorEvent();
            event.eventType = MonitorEvent.Type.LATE_SOURCE.name();
            event.detail = "构建 " + buildId + " 有 " + late.size()
                    + " 个来源在截止点后到达，已登记为事实 v" + fact.factVersion + " 的后续差异来源";
            event.affectedViews = "[]";
            monitorRepo.save(event);
        }
        return fact;
    }

    /** 摄入钩子：若该构建已有签发事实且来源到达晚于截止点，登记为后续差异来源。 */
    @Transactional
    public void registerLateSourceIfNeeded(SourceArtifact artifact) {
        if (artifact.buildId == null || artifact.buildId.isBlank()) return;
        List<BuildFact> facts = factRepo.findByBuildIdOrderByFactVersionDesc(artifact.buildId);
        for (BuildFact fact : facts) {
            if (artifact.collectedAt.isAfter(fact.cutoffTime)) {
                List<Object> late = new ArrayList<>();
                if (fact.postCutoffSources != null && !fact.postCutoffSources.isBlank()) {
                    late.addAll(json.read(fact.postCutoffSources).isArray()
                            ? json.mapper().convertValue(json.read(fact.postCutoffSources),
                                    List.class) : List.of());
                }
                boolean already = late.stream().anyMatch(o -> o instanceof Map
                        && artifact.id.equals(((Map<?, ?>) o).get("id")));
                if (!already) {
                    Map<String, Object> ref = new LinkedHashMap<>(sourceRef(artifact));
                    ref.put("note", "事实签发后到达，登记为后续差异来源");
                    late.add(ref);
                    fact.postCutoffSources = json.write(late);
                    factRepo.save(fact);
                    MonitorEvent event = new MonitorEvent();
                    event.eventType = MonitorEvent.Type.LATE_SOURCE.name();
                    event.detail = "来源 " + artifact.type + "#" + artifact.id.substring(0, 8)
                            + " 在事实 v" + fact.factVersion + " 截止点后到达构建 " + artifact.buildId
                            + "，已登记为后续差异来源";
                    event.affectedViews = "[]";
                    monitorRepo.save(event);
                    reviewService.record("BUILD_FACT", fact.id, "LATE_SOURCE_REGISTERED",
                            artifact.type + " hash=" + artifact.payloadHash.substring(0, 12), "system");
                }
            }
        }
    }

    private Map<String, Object> sourceRef(SourceArtifact s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("type", s.type);
        m.put("hash", s.payloadHash);
        m.put("collectedAt", s.collectedAt.toString());
        m.put("sourceUri", s.sourceUri);
        return m;
    }

    private Map<String, Object> identitySnapshot(ComponentIdentity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id);
        m.put("identityKey", i.identityKey);
        m.put("coordinate", i.coordinate());
        m.put("ecosystem", i.ecosystem);
        m.put("namespace", i.namespace);
        m.put("name", i.name);
        m.put("version", i.version);
        m.put("publisher", i.publisher);
        m.put("hashAlg", i.hashAlg);
        m.put("hashValue", i.hashValue);
        m.put("status", i.status);
        m.put("versionNo", i.versionNo);
        m.put("conflictReason", i.conflictReason);
        return m;
    }

    private Map<String, Object> edgeSnapshot(DependencyEdge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("buildId", e.buildId);
        m.put("fromKey", e.fromKey);
        m.put("toIdentityId", e.toIdentityId);
        m.put("toLabel", e.toLabel);
        m.put("scope", e.scope);
        m.put("edgeStatus", e.edgeStatus);
        m.put("evidenceSummary", e.evidenceSummary);
        return m;
    }
}
