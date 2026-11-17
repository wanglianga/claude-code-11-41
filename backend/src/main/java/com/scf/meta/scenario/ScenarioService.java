package com.scf.meta.scenario;

import com.scf.meta.buildfact.BuildFact;
import com.scf.meta.buildfact.BuildFactItem;
import com.scf.meta.buildfact.BuildFactItemRepository;
import com.scf.meta.buildfact.BuildFactRepository;
import com.scf.meta.buildfact.IssuerService;
import com.scf.meta.catalog.CatalogEntry;
import com.scf.meta.catalog.CatalogEntryRepository;
import com.scf.meta.catalog.CatalogService;
import com.scf.meta.catalog.ConsumerView;
import com.scf.meta.catalog.ConsumerViewRepository;
import com.scf.meta.common.Json;
import com.scf.meta.dependency.CompilerService;
import com.scf.meta.dependency.DependencyEdge;
import com.scf.meta.dependency.DependencyEdgeRepository;
import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.signature.SignatureRecord;
import com.scf.meta.signature.SignatureRecordRepository;
import com.scf.meta.signature.SignatureService;
import com.scf.meta.source.IngestService;
import com.scf.meta.source.SourceArtifact;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 验收场景编排：同名异发布者、哈希冲突、私有镜像、构建后补传、
 * 目录别名修订、签名撤销。每个场景使用独立构建标识，可重复运行。
 */
@Service
public class ScenarioService {

    private final IngestService ingestService;
    private final CompilerService compilerService;
    private final IssuerService issuerService;
    private final CatalogService catalogService;
    private final SignatureService signatureService;
    private final ComponentIdentityRepository identityRepo;
    private final DependencyEdgeRepository edgeRepo;
    private final BuildFactRepository factRepo;
    private final BuildFactItemRepository itemRepo;
    private final ConsumerViewRepository viewRepo;
    private final CatalogEntryRepository entryRepo;
    private final SignatureRecordRepository signatureRepo;
    private final Json json;

    public ScenarioService(IngestService ingestService, CompilerService compilerService,
                           IssuerService issuerService, CatalogService catalogService,
                           SignatureService signatureService,
                           ComponentIdentityRepository identityRepo,
                           DependencyEdgeRepository edgeRepo,
                           BuildFactRepository factRepo,
                           BuildFactItemRepository itemRepo,
                           ConsumerViewRepository viewRepo,
                           CatalogEntryRepository entryRepo,
                           SignatureRecordRepository signatureRepo,
                           Json json) {
        this.ingestService = ingestService;
        this.compilerService = compilerService;
        this.issuerService = issuerService;
        this.catalogService = catalogService;
        this.signatureService = signatureService;
        this.identityRepo = identityRepo;
        this.edgeRepo = edgeRepo;
        this.factRepo = factRepo;
        this.itemRepo = itemRepo;
        this.viewRepo = viewRepo;
        this.entryRepo = entryRepo;
        this.signatureRepo = signatureRepo;
        this.json = json;
    }

    public static class ScenarioResult {
        public String scenario;
        public String buildId;
        public List<String> steps = new ArrayList<>();
        public List<Map<String, Object>> checks = new ArrayList<>();
        public Map<String, Object> objects = new LinkedHashMap<>();

        void check(String name, boolean passed, String detail) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", name);
            c.put("passed", passed);
            c.put("detail", detail);
            checks.add(c);
        }
    }

    public ScenarioResult run(String name) {
        return switch (name) {
            case "same-name-diff-publisher" -> sameNameDiffPublisher();
            case "hash-conflict" -> hashConflict();
            case "private-mirror" -> privateMirror();
            case "late-lockfile" -> lateLockfile();
            case "catalog-alias-revision" -> catalogAliasRevision();
            case "signature-revocation" -> signatureRevocation();
            default -> throw new IllegalArgumentException("未知场景: " + name);
        };
    }

    private String newBuildId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private SourceArtifact ingest(String type, String eco, String buildId, String payload,
                                  Instant collectedAt, String uri) {
        return ingestService.ingest(new IngestService.IngestCommand(
                type, uri, eco, buildId, payload,
                collectedAt, collectedAt, "scenario"));
    }

    /** 场景1：同名包在 npm 与内部包仓发布者/哈希不同且缺少生态上下文 → 多个候选并存。 */
    private ScenarioResult sameNameDiffPublisher() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "same-name-diff-publisher";
        String buildId = newBuildId("sndp");
        r.buildId = buildId;

        SourceArtifact npm = ingest("REPO_METADATA", "npm", buildId,
                "{\"packages\":[{\"name\":\"shared-lib\",\"version\":\"1.0.0\","
                        + "\"publisher\":\"npmjs-official\",\"ecosystem\":\"npm\","
                        + "\"dist\":{\"integrity\":\"sha256-aaaa1111\"}}]}",
                Instant.now(), "https://registry.npmjs.org/shared-lib");
        r.steps.add("摄入 npm 包仓元数据: shared-lib@1.0.0 publisher=npmjs-official");

        SourceArtifact mirror = ingest("REPO_METADATA", null, buildId,
                "{\"mirror\":\"internal\",\"packages\":[{\"name\":\"shared-lib\",\"version\":\"1.0.0\","
                        + "\"publisher\":\"internal-platform-team\","
                        + "\"dist\":{\"shasum\":\"bbbb2222\"}}]}",
                Instant.now(), "https://mirror.internal.corp/repository/npm/shared-lib");
        r.steps.add("摄入内部镜像包仓元数据（缺生态上下文）: shared-lib@1.0.0 publisher=internal-platform-team");

        List<ComponentIdentity> identities = identityRepo.findByName("shared-lib").stream()
                .filter(i -> i.identityKey.contains("1.0.0")).toList();
        long candidates = identities.stream()
                .filter(i -> "CANDIDATE".equals(i.status)).count();
        r.check("保留多个候选身份", identities.size() >= 2 && candidates >= 2,
                "身份数=" + identities.size() + " 候选数=" + candidates);
        r.check("候选未被合并为已确认身份",
                identities.stream().noneMatch(i -> "CONFIRMED".equals(i.status)),
                "无 CONFIRMED 状态身份");
        r.check("私有镜像来源留下待核项", mirror.note != null && mirror.note.contains("待核"),
                "note=" + mirror.note);
        r.objects.put("npmSourceId", npm.id);
        r.objects.put("mirrorSourceId", mirror.id);
        r.objects.put("identityIds", identities.stream().map(i -> i.id).toList());
        return r;
    }

    /** 场景2：SBOM 与锁文件坐标相同哈希不同，构建日志不能证明任一哈希 → 来源冲突，不签发已证实依赖。 */
    private ScenarioResult hashConflict() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "hash-conflict";
        String buildId = newBuildId("hashconflict");
        r.buildId = buildId;

        ingest("SBOM", "npm", buildId,
                "{\"components\":[{\"name\":\"crypto-util\",\"version\":\"2.0.0\","
                        + "\"publisher\":\"upstream\",\"ecosystem\":\"npm\","
                        + "\"hashes\":[{\"alg\":\"SHA-256\",\"content\":\"hashA001\"}]}]}",
                Instant.now(), "https://ci.corp/jobs/" + buildId + "/sbom.json");
        r.steps.add("摄入 SBOM: crypto-util@2.0.0 sha256=hashA001");

        ingest("LOCKFILE", "npm", buildId,
                "{\"dependencies\":[{\"name\":\"crypto-util\",\"version\":\"2.0.0\","
                        + "\"publisher\":\"upstream\",\"ecosystem\":\"npm\","
                        + "\"integrity\":\"sha256-hashB002\"}]}",
                Instant.now(), "https://ci.corp/jobs/" + buildId + "/package-lock.json");
        r.steps.add("摄入锁文件: crypto-util@2.0.0 sha256=hashB002（坐标相同、哈希不同）");

        ingest("BUILD_LOG", "npm", buildId,
                "Resolved npm:crypto-util@2.0.0\nBuild completed in 42s",
                Instant.now(), "https://ci.corp/jobs/" + buildId + "/build.log");
        r.steps.add("摄入构建日志: 仅显示解析了坐标，未记录任一哈希");

        List<ComponentIdentity> identities = identityRepo.findByName("crypto-util").stream()
                .filter(i -> i.identityKey.contains("2.0.0")).toList();
        boolean conflict = identities.stream().anyMatch(i -> "CONFLICT".equals(i.status));
        r.check("身份工作区标为来源冲突", conflict,
                "状态=" + identities.stream().map(i -> i.status).distinct().toList());

        CompilerService.CompileResult compiled = compilerService.compile(buildId, null, "scenario");
        boolean noProven = compiled.edges().stream()
                .noneMatch(e -> "PROVEN".equals(e.edgeStatus));
        boolean hasRejected = compiled.edges().stream()
                .anyMatch(e -> "REJECTED".equals(e.edgeStatus));
        r.check("编译器不签发已证实依赖", noProven && hasRejected,
                "边状态=" + compiled.edges().stream().map(e -> e.edgeStatus).toList());

        BuildFact fact = issuerService.issue(buildId, null, "scenario");
        List<BuildFactItem> items = itemRepo.findByBuildFactId(fact.id);
        boolean factNoProven = items.stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .noneMatch(i -> json.read(i.snapshot).get("edgeStatus").asText().equals("PROVEN"));
        r.check("签发事实中无已证实依赖边", factNoProven, "factId=" + fact.id);
        r.objects.put("factId", fact.id);
        r.objects.put("identityIds", identities.stream().map(i -> i.id).toList());
        return r;
    }

    /** 场景3：私有镜像字段变体 → 单独映射、留下待核项、保持候选。 */
    private ScenarioResult privateMirror() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "private-mirror";
        String buildId = newBuildId("mirror");
        r.buildId = buildId;

        SourceArtifact mirror = ingest("REPO_METADATA", null, buildId,
                "{\"mirror\":\"nexus-internal\",\"packages\":[{\"name\":\"legacy-sdk\","
                        + "\"version\":\"0.9.1\",\"owner\":\"infra-group\","
                        + "\"dist\":{\"shasum\":\"cccc3333\"}}]}",
                Instant.now(), "https://mirror.internal.corp/repository/npm/legacy-sdk");
        r.steps.add("摄入私有镜像元数据: 字段为 owner/dist.shasum 变体且缺生态上下文");

        boolean warnings = "PARSED_WITH_WARNINGS".equals(mirror.ingestStatus);
        r.check("私有镜像字段变化被单独映射并记录待核项",
                warnings && mirror.note != null && mirror.note.contains("待核"),
                "ingestStatus=" + mirror.ingestStatus + " note=" + mirror.note);

        List<ComponentIdentity> identities = identityRepo.findByName("legacy-sdk");
        boolean candidate = identities.stream().allMatch(i -> "CANDIDATE".equals(i.status))
                && !identities.isEmpty();
        r.check("镜像声明保持候选不误合并", candidate,
                "状态=" + identities.stream().map(i -> i.status).toList());
        r.objects.put("mirrorSourceId", mirror.id);
        r.objects.put("identityIds", identities.stream().map(i -> i.id).toList());
        return r;
    }

    /** 场景4：构建日志显示解析完成后锁文件补传且版本不同 → 原事实按截止点证据签发，补传件登记为后续差异。 */
    private ScenarioResult lateLockfile() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "late-lockfile";
        String buildId = newBuildId("late");
        r.buildId = buildId;

        Instant t0 = Instant.now().minus(2, ChronoUnit.HOURS);
        ingest("BUILD_LOG", "maven", buildId,
                "Resolved maven:com.acme/order-service@1.2.3 sha256=dddd4444 publisher=acme-release\n"
                        + "Resolution completed.",
                t0, "https://ci.corp/jobs/" + buildId + "/build.log");
        r.steps.add("T0 摄入构建日志: 解析完成 order-service@1.2.3 sha256=dddd4444");

        Instant cutoff = t0.plus(30, ChronoUnit.MINUTES);
        BuildFact fact = issuerService.issue(buildId, cutoff, "scenario");
        r.steps.add("T0+30m 以截止点签发构建事实 v" + fact.factVersion);

        Instant t2 = cutoff.plus(1, ChronoUnit.HOURS);
        SourceArtifact lateLock = ingest("LOCKFILE", "maven", buildId,
                "{\"dependencies\":[{\"name\":\"order-service\",\"version\":\"1.2.4\","
                        + "\"namespace\":\"com.acme\",\"ecosystem\":\"maven\","
                        + "\"integrity\":\"sha256-eeee5555\"}]}",
                t2, "https://ci.corp/jobs/" + buildId + "/late-lock.json");
        r.steps.add("T0+90m 锁文件补传: order-service@1.2.4（版本不同）");

        BuildFact reloaded = factRepo.findById(fact.id).orElseThrow();
        List<BuildFactItem> items = itemRepo.findByBuildFactId(fact.id);
        boolean proven123 = items.stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .anyMatch(i -> {
                    var n = json.read(i.snapshot);
                    return "PROVEN".equals(n.get("edgeStatus").asText())
                            && n.get("toLabel").asText().contains("1.2.3");
                });
        r.check("原事实以截止点可见证据签发（1.2.3 已证实）", proven123,
                "事实边快照含 1.2.3 PROVEN=" + proven123);

        boolean lateRegistered = reloaded.postCutoffSources != null
                && reloaded.postCutoffSources.contains(lateLock.id);
        r.check("补传锁文件登记为后续差异来源", lateRegistered,
                "postCutoffSources=" + reloaded.postCutoffSources);

        boolean no124 = items.stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .noneMatch(i -> json.read(i.snapshot).get("toLabel").asText().contains("1.2.4"));
        r.check("原事实未被补传件改写（无 1.2.4 边）", no124, "事实条目数=" + items.size());
        r.objects.put("factId", fact.id);
        r.objects.put("lateLockfileSourceId", lateLock.id);
        return r;
    }

    /** 场景5：目录别名/发布者修订 → 监测器定位当前引用并暂停下游视图，历史保留当时身份版本。 */
    private ScenarioResult catalogAliasRevision() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "catalog-alias-revision";
        String buildId = newBuildId("catalog");
        r.buildId = buildId;

        ingest("SBOM", "npm", buildId,
                "{\"components\":[{\"name\":\"web-framework\",\"version\":\"5.1.0\","
                        + "\"publisher\":\"old-alias-team\",\"ecosystem\":\"npm\","
                        + "\"hashes\":[{\"alg\":\"SHA-256\",\"content\":\"ffff6666\"}]}]}",
                Instant.now(), "https://ci.corp/jobs/" + buildId + "/sbom.json");
        r.steps.add("摄入 SBOM: web-framework@5.1.0 publisher=old-alias-team");

        CatalogEntry entry = catalogService.createEntry("npm", "", "web-framework",
                "old-alias-team", "[\"web-fw\"]", "scenario");
        r.steps.add("登记组件目录条目: web-framework 别名 [web-fw]");

        BuildFact fact = issuerService.issue(buildId, null, "scenario");
        ConsumerView view = catalogService.createView("RISK_VIEW", "风险视图-" + buildId,
                fact.id, "scenario");
        r.steps.add("签发事实 v" + fact.factVersion + " 并创建下游风险视图");

        catalogService.reviseEntry(entry.id, "new-platform-org",
                "[\"web-fw\", \"web-framework-next\"]", "发布者归属变更", "scenario");
        r.steps.add("目录修订: 发布者 old-alias-team → new-platform-org，新增别名");

        ConsumerView reloaded = viewRepo.findById(view.id).orElseThrow();
        r.check("变动监测器暂停当前下游视图", "SUSPENDED".equals(reloaded.status),
                "视图状态=" + reloaded.status + " 原因=" + reloaded.suspendReason);
        boolean snapshotKept = reloaded.identityVersions != null
                && reloaded.identityVersions.contains("\"");
        r.check("历史视图保留当时身份版本快照", snapshotKept,
                "identityVersions=" + reloaded.identityVersions);
        CatalogEntry updated = entryRepo.findById(entry.id).orElseThrow();
        r.check("目录修订记录版本递增", updated.revision == 2, "revision=" + updated.revision);
        r.objects.put("factId", fact.id);
        r.objects.put("viewId", view.id);
        r.objects.put("catalogEntryId", entry.id);
        return r;
    }

    /** 场景6：作为唯一依据的签名撤销 → 身份降为证据不足，新事实不能继续显示已证实。 */
    private ScenarioResult signatureRevocation() {
        ScenarioResult r = new ScenarioResult();
        r.scenario = "signature-revocation";
        String buildId = newBuildId("sigrevoke");
        r.buildId = buildId;
        // 每次运行使用唯一主体哈希，避免与历史运行的签名记录冲突
        String subjectHash = "sig" + UUID.randomUUID().toString().substring(0, 8);

        ingest("SIGNATURE_INDEX", "npm", buildId,
                "{\"signatures\":[{\"subjectHash\":\"" + subjectHash + "\",\"signer\":\"release-key-1\","
                        + "\"component\":{\"name\":\"secure-bundle\",\"version\":\"3.0.0\","
                        + "\"ecosystem\":\"npm\",\"publisher\":\"security-team\"}}]}",
                Instant.now(), "https://sigstore.corp/index/secure-bundle");
        r.steps.add("摄入签名索引: secure-bundle@3.0.0 唯一依据为签名 " + subjectHash);

        ComponentIdentity identity = identityRepo.findByName("secure-bundle").stream()
                .filter(i -> i.identityKey.contains(subjectHash)).findFirst().orElseThrow();
        r.check("签名有效时身份已确认", "CONFIRMED".equals(identity.status),
                "状态=" + identity.status);

        BuildFact fact1 = issuerService.issue(buildId, null, "scenario");
        boolean provenBefore = itemRepo.findByBuildFactId(fact1.id).stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .anyMatch(i -> "PROVEN".equals(json.read(i.snapshot).get("edgeStatus").asText()));
        r.check("撤销前事实含已证实依赖边", provenBefore, "fact v" + fact1.factVersion);

        SignatureRecord sig = signatureRepo.findBySubjectHash(subjectHash).stream()
                .filter(s -> "VALID".equals(s.status)).findFirst().orElseThrow();
        signatureService.revoke(sig.id, "scenario");
        r.steps.add("撤销签名 " + subjectHash);

        ComponentIdentity downgraded = identityRepo.findById(identity.id).orElseThrow();
        r.check("受影响组件降为证据不足", "INSUFFICIENT_EVIDENCE".equals(downgraded.status),
                "状态=" + downgraded.status + " versionNo=" + downgraded.versionNo);

        BuildFact fact2 = issuerService.issue(buildId, null, "scenario");
        boolean noProvenAfter = itemRepo.findByBuildFactId(fact2.id).stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .noneMatch(i -> "PROVEN".equals(json.read(i.snapshot).get("edgeStatus").asText()));
        r.check("撤销后新事实不能继续显示已证实", noProvenAfter,
                "fact v" + fact2.factVersion + " 无 PROVEN 边");
        r.objects.put("factIdBefore", fact1.id);
        r.objects.put("factIdAfter", fact2.id);
        r.objects.put("identityId", identity.id);
        r.objects.put("signatureId", sig.id);
        return r;
    }
}
