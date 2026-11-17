package com.scf.meta.identity;

import com.scf.meta.review.ReviewService;
import com.scf.meta.signature.SignatureRecord;
import com.scf.meta.signature.SignatureRecordRepository;
import com.scf.meta.source.SourceArtifact;
import com.scf.meta.source.SourceArtifactRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组件身份工作区规则引擎（rules-1.0）：
 * 1. 声明按 生态|命名空间|名称|版本 分组，再按 发布者|哈希 归并为身份；
 * 2. 实际解析证据（SBOM/锁文件/构建日志）哈希一致 → CONFIRMED；
 * 3. 解析证据间哈希冲突且构建日志不能证明任一哈希 → 全部 CONFLICT（来源冲突）；
 * 4. 构建日志确认某一哈希 → 该哈希身份 CONFIRMED，其余保留 CONFLICT 说明；
 * 5. 仅包仓/扫描推断 → CANDIDATE；同名异发布者且缺生态上下文 → 多个候选并存；
 * 6. 签名作为唯一依据可确认；签名撤销后降为 INSUFFICIENT_EVIDENCE。
 */
@Service
public class IdentityResolutionService {

    private final ComponentClaimRepository claimRepo;
    private final ComponentIdentityRepository identityRepo;
    private final SourceArtifactRepository sourceRepo;
    private final SignatureRecordRepository signatureRepo;
    private final ReviewService reviewService;

    public IdentityResolutionService(ComponentClaimRepository claimRepo,
                                     ComponentIdentityRepository identityRepo,
                                     SourceArtifactRepository sourceRepo,
                                     SignatureRecordRepository signatureRepo,
                                     ReviewService reviewService) {
        this.claimRepo = claimRepo;
        this.identityRepo = identityRepo;
        this.sourceRepo = sourceRepo;
        this.signatureRepo = signatureRepo;
        this.reviewService = reviewService;
    }

    @Transactional
    public void resolveForClaims(List<ComponentClaim> claims, String actor) {
        Set<String> names = claims.stream().map(c -> c.name).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String name : names) {
            // 按名称取出全部声明后在内存中分组（规模可控，规则清晰）
            List<ComponentClaim> all = claimRepo.findByName(name);
            Map<String, List<ComponentClaim>> groups = all.stream()
                    .collect(Collectors.groupingBy(ComponentClaim::groupKey, LinkedHashMap::new,
                            Collectors.toList()));
            for (List<ComponentClaim> group : groups.values()) {
                if (group.stream().anyMatch(c -> claims.stream().anyMatch(n -> n.id.equals(c.id)))) {
                    resolveGroup(group, actor);
                }
            }
        }
    }

    @Transactional
    public void resolveGroup(List<ComponentClaim> groupClaims, String actor) {
        // 1. 归并：每个 (publisher, hash) 组合对应一个身份
        Map<String, ComponentIdentity> identities = new LinkedHashMap<>();
        Map<String, List<ComponentClaim>> claimsByIdentity = new HashMap<>();
        for (ComponentClaim c : groupClaims) {
            String key = ComponentIdentity.keyOf(c.ecosystem, c.namespace, c.name, c.version,
                    c.publisher, c.hashValue);
            ComponentIdentity identity = identities.get(key);
            if (identity == null) {
                identity = identityRepo.findByIdentityKey(key).orElse(null);
            }
            if (identity == null) {
                identity = new ComponentIdentity();
                identity.identityKey = key;
                identity.ecosystem = normEco(c.ecosystem);
                identity.namespace = c.namespace == null ? "" : c.namespace;
                identity.name = c.name;
                identity.version = c.version;
                identity.publisher = c.publisher;
                identity.hashAlg = c.hashAlg;
                identity.hashValue = c.hashValue;
                identity.status = ComponentIdentity.Status.CANDIDATE.name();
                identityRepo.save(identity);
                reviewService.record("IDENTITY", identity.id, "CREATED",
                        "候选身份建立: " + identity.coordinate(), actor);
            }
            identities.put(key, identity);
            claimsByIdentity.computeIfAbsent(identity.id, k -> new ArrayList<>()).add(c);
            if (!identity.id.equals(c.identityId)) {
                c.identityId = identity.id;
                claimRepo.save(c);
            }
        }

        // 2. 组内证据评估
        List<ComponentClaim> resolved = groupClaims.stream()
                .filter(c -> ComponentClaim.Kind.RESOLVED.name().equals(c.kind)).toList();
        Map<String, String> sourceTypeById = new HashMap<>();
        for (ComponentClaim c : groupClaims) {
            sourceTypeById.computeIfAbsent(c.sourceArtifactId, id -> sourceRepo.findById(id)
                    .map(s -> s.type).orElse("?"));
        }
        Set<String> resolvedHashes = resolved.stream().map(c -> c.hashValue)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> buildLogHashes = resolved.stream()
                .filter(c -> "BUILD_LOG".equals(sourceTypeById.get(c.sourceArtifactId)))
                .map(c -> c.hashValue).filter(Objects::nonNull).collect(Collectors.toSet());

        boolean hashConflict = resolvedHashes.size() > 1;
        for (Map.Entry<String, ComponentIdentity> e : identities.entrySet()) {
            ComponentIdentity identity = e.getValue();
            List<ComponentClaim> own = claimsByIdentity.getOrDefault(identity.id, List.of());
            boolean hasResolved = own.stream()
                    .anyMatch(c -> ComponentClaim.Kind.RESOLVED.name().equals(c.kind));
            boolean hasSignature = own.stream()
                    .anyMatch(c -> ComponentClaim.Kind.SIGNATURE.name().equals(c.kind));

            String newStatus;
            String reason = null;
            if (hashConflict) {
                if (identity.hashValue != null && buildLogHashes.contains(identity.hashValue)) {
                    newStatus = ComponentIdentity.Status.CONFIRMED.name();
                    reason = null;
                } else {
                    newStatus = ComponentIdentity.Status.CONFLICT.name();
                    reason = "来源冲突: 坐标 " + identity.coordinate() + " 的哈希在解析证据间不一致 "
                            + resolvedHashes + "，构建日志无法证明该哈希被实际解析";
                }
            } else if (hasResolved) {
                newStatus = ComponentIdentity.Status.CONFIRMED.name();
            } else if (hasSignature && signatureValidFor(identity)) {
                newStatus = ComponentIdentity.Status.CONFIRMED.name();
            } else {
                newStatus = ComponentIdentity.Status.CANDIDATE.name();
            }
            applyStatus(identity, newStatus, reason, actor);
        }
    }

    private boolean signatureValidFor(ComponentIdentity identity) {
        if (identity.hashValue == null) return false;
        List<SignatureRecord> sigs = signatureRepo.findBySubjectHash(identity.hashValue);
        boolean anyValid = sigs.stream().anyMatch(s -> "VALID".equals(s.status));
        if (anyValid && identity.signatureId == null) {
            identity.signatureId = sigs.stream().filter(s -> "VALID".equals(s.status))
                    .findFirst().get().id;
        }
        return anyValid;
    }

    private void applyStatus(ComponentIdentity identity, String newStatus, String reason, String actor) {
        boolean changed = !newStatus.equals(identity.status);
        identity.conflictReason = reason;
        if (changed) {
            String old = identity.status;
            identity.status = newStatus;
            identity.versionNo++;
            identity.updatedAt = java.time.Instant.now();
            identityRepo.save(identity);
            reviewService.record("IDENTITY", identity.id, "STATUS_" + newStatus,
                    old + " -> " + newStatus + (reason == null ? "" : ": " + reason), actor);
        } else {
            identityRepo.save(identity);
        }
    }

    /** 签名撤销：以其为唯一依据（无实际解析证据）的身份降为证据不足。 */
    @Transactional
    public List<ComponentIdentity> handleSignatureRevocation(String signatureId, String actor) {
        List<ComponentIdentity> affected = new ArrayList<>();
        for (ComponentIdentity identity : identityRepo.findBySignatureId(signatureId)) {
            List<ComponentClaim> claims = claimRepo.findByIdentityId(identity.id);
            boolean hasResolved = claims.stream()
                    .anyMatch(c -> ComponentClaim.Kind.RESOLVED.name().equals(c.kind));
            if (!hasResolved && ComponentIdentity.Status.CONFIRMED.name().equals(identity.status)) {
                applyStatus(identity, ComponentIdentity.Status.INSUFFICIENT_EVIDENCE.name(),
                        "唯一依据的签名 " + signatureId + " 已撤销，降为证据不足", actor);
                affected.add(identity);
            }
        }
        return affected;
    }

    private static String normEco(String eco) {
        return eco == null || eco.isBlank() ? "unknown" : eco;
    }
}
