package com.scf.meta.buildfact;

import com.scf.meta.catalog.ConsumerView;
import com.scf.meta.catalog.ConsumerViewRepository;
import com.scf.meta.common.ApiResponse;
import com.scf.meta.common.AuthInterceptor;
import com.scf.meta.common.Json;
import com.scf.meta.identity.ComponentClaimRepository;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.review.ReviewEventRepository;
import com.scf.meta.source.SourceArtifactRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/build-facts")
public class BuildFactController {

    private final IssuerService issuerService;
    private final BuildFactRepository factRepo;
    private final BuildFactItemRepository itemRepo;
    private final SourceArtifactRepository sourceRepo;
    private final ComponentIdentityRepository identityRepo;
    private final ComponentClaimRepository claimRepo;
    private final ConsumerViewRepository viewRepo;
    private final ReviewEventRepository reviewRepo;
    private final Json json;

    public BuildFactController(IssuerService issuerService, BuildFactRepository factRepo,
                               BuildFactItemRepository itemRepo,
                               SourceArtifactRepository sourceRepo,
                               ComponentIdentityRepository identityRepo,
                               ComponentClaimRepository claimRepo,
                               ConsumerViewRepository viewRepo,
                               ReviewEventRepository reviewRepo, Json json) {
        this.issuerService = issuerService;
        this.factRepo = factRepo;
        this.itemRepo = itemRepo;
        this.sourceRepo = sourceRepo;
        this.identityRepo = identityRepo;
        this.claimRepo = claimRepo;
        this.viewRepo = viewRepo;
        this.reviewRepo = reviewRepo;
        this.json = json;
    }

    public record IssueRequest(@NotBlank String buildId, String cutoffTime) {}

    @PostMapping("/issue")
    public ApiResponse<BuildFact> issue(@RequestBody IssueRequest req, HttpServletRequest http) {
        String actor = (String) http.getAttribute(AuthInterceptor.ATTR_USER);
        Instant cutoff = (req.cutoffTime() == null || req.cutoffTime().isBlank())
                ? null : Instant.parse(req.cutoffTime());
        return ApiResponse.ok(issuerService.issue(req.buildId(), cutoff, actor));
    }

    @GetMapping
    public ApiResponse<List<BuildFact>> list(@RequestParam(required = false) String buildId) {
        if (buildId != null && !buildId.isBlank()) {
            return ApiResponse.ok(factRepo.findByBuildIdOrderByFactVersionDesc(buildId));
        }
        return ApiResponse.ok(factRepo.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        BuildFact fact = factRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("构建事实不存在: " + id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fact", fact);
        result.put("items", itemRepo.findByBuildFactId(id));
        return ApiResponse.ok(result);
    }

    /** 全链路追溯：来源、身份关系、依赖边、冻结版本、复审事件、下游引用。 */
    @GetMapping("/{id}/trace")
    public ApiResponse<Map<String, Object>> trace(@PathVariable String id) {
        BuildFact fact = factRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("构建事实不存在: " + id));
        List<BuildFactItem> items = itemRepo.findByBuildFactId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fact", fact);
        result.put("frozenItems", items);

        // 来源对象（含后续差异来源）
        List<Map<String, Object>> sources = new ArrayList<>();
        for (String artifactId : extractIds(fact.sourceHashes)) {
            sourceRepo.findById(artifactId).ifPresent(s -> sources.add(Map.of(
                    "role", "cutoff-visible", "artifact", s)));
        }
        for (String artifactId : extractIds(fact.postCutoffSources)) {
            sourceRepo.findById(artifactId).ifPresent(s -> sources.add(Map.of(
                    "role", "post-cutoff-difference", "artifact", s)));
        }
        result.put("sources", sources);

        // 身份关系：冻结身份 + 当前状态 + 各自声明
        List<Map<String, Object>> identities = new ArrayList<>();
        List<Object> edges = new ArrayList<>();
        for (BuildFactItem item : items) {
            if ("IDENTITY".equals(item.itemKind)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("frozen", json.read(item.snapshot));
                identityRepo.findById(item.refId).ifPresent(current -> {
                    entry.put("current", current);
                    entry.put("claims", claimRepo.findByIdentityId(item.refId));
                });
                identities.add(entry);
            } else if ("EDGE".equals(item.itemKind)) {
                edges.add(json.read(item.snapshot));
            }
        }
        result.put("identities", identities);
        result.put("edges", edges);

        // 复审事件（事实本身 + 各身份）
        List<Map<String, Object>> reviews = new ArrayList<>();
        reviewRepo.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc("BUILD_FACT", id)
                .forEach(e -> reviews.add(subject("BUILD_FACT", e)));
        for (BuildFactItem item : items) {
            if ("IDENTITY".equals(item.itemKind)) {
                reviewRepo.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc("IDENTITY", item.refId)
                        .forEach(e -> reviews.add(subject("IDENTITY", e)));
            }
        }
        result.put("reviewEvents", reviews);

        // 下游引用
        List<ConsumerView> views = viewRepo.findByBuildFactId(id);
        result.put("consumerViews", views);
        return ApiResponse.ok(result);
    }

    private Map<String, Object> subject(String type, com.scf.meta.review.ReviewEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subjectType", type);
        m.put("subjectId", e.subjectId);
        m.put("action", e.action);
        m.put("detail", e.detail);
        m.put("actor", e.actor);
        m.put("createdAt", e.createdAt.toString());
        return m;
    }

    private List<String> extractIds(String jsonArray) {
        List<String> ids = new ArrayList<>();
        if (jsonArray == null || jsonArray.isBlank()) return ids;
        for (var node : json.read(jsonArray)) {
            if (node.has("id")) ids.add(node.get("id").asText());
        }
        return ids;
    }
}
