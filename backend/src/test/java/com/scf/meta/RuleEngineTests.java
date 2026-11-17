package com.scf.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scf.meta.buildfact.BuildFact;
import com.scf.meta.buildfact.BuildFactItemRepository;
import com.scf.meta.buildfact.BuildFactRepository;
import com.scf.meta.buildfact.IssuerService;
import com.scf.meta.catalog.CatalogEntry;
import com.scf.meta.catalog.CatalogService;
import com.scf.meta.catalog.ConsumerView;
import com.scf.meta.catalog.ConsumerViewRepository;
import com.scf.meta.dependency.CompilerService;
import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.identity.ComponentIdentityRepository;
import com.scf.meta.scenario.ScenarioService;
import com.scf.meta.signature.SignatureRecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 核心规则引擎验收测试（H2 内存库）。 */
@SpringBootTest
@ActiveProfiles("test")
class RuleEngineTests {

    @Autowired ScenarioService scenarioService;
    @Autowired ComponentIdentityRepository identityRepo;
    @Autowired BuildFactRepository factRepo;
    @Autowired BuildFactItemRepository itemRepo;
    @Autowired ConsumerViewRepository viewRepo;
    @Autowired SignatureRecordRepository signatureRepo;
    @Autowired IssuerService issuerService;
    @Autowired CompilerService compilerService;
    @Autowired CatalogService catalogService;

    @Test
    void sameNameDiffPublisherKeepsMultipleCandidates() {
        ScenarioService.ScenarioResult r = scenarioService.run("same-name-diff-publisher");
        assertAllPassed(r);
        List<ComponentIdentity> identities = identityRepo.findByName("shared-lib");
        assertTrue(identities.size() >= 2, "应保留多个候选身份");
        assertTrue(identities.stream().allMatch(i -> "CANDIDATE".equals(i.status)));
    }

    @Test
    void hashConflictBlocksProvenDependency() {
        ScenarioService.ScenarioResult r = scenarioService.run("hash-conflict");
        assertAllPassed(r);
        List<ComponentIdentity> identities = identityRepo.findByName("crypto-util");
        assertTrue(identities.stream().anyMatch(i -> "CONFLICT".equals(i.status)),
                "应标为来源冲突");
        String factId = (String) r.objects.get("factId");
        assertNotNull(factId);
        assertTrue(itemRepo.findByBuildFactId(factId).stream()
                .filter(i -> "EDGE".equals(i.itemKind))
                .noneMatch(i -> i.snapshot.contains("\"PROVEN\"")), "事实中不得有已证实边");
    }

    @Test
    void privateMirrorLeavesPendingCandidates() {
        ScenarioService.ScenarioResult r = scenarioService.run("private-mirror");
        assertAllPassed(r);
    }

    @Test
    void lateLockfileRegisteredAsPostCutoffDifference() {
        ScenarioService.ScenarioResult r = scenarioService.run("late-lockfile");
        assertAllPassed(r);
        String factId = (String) r.objects.get("factId");
        BuildFact fact = factRepo.findById(factId).orElseThrow();
        assertTrue(fact.postCutoffSources.contains((String) r.objects.get("lateLockfileSourceId")),
                "补传件应登记为后续差异来源");
        assertTrue(itemRepo.findByBuildFactId(factId).stream()
                .anyMatch(i -> i.snapshot.contains("1.2.3") && i.snapshot.contains("PROVEN")),
                "原事实应保留截止点时已证实的 1.2.3");
    }

    @Test
    void catalogRevisionSuspendsDownstreamViews() {
        ScenarioService.ScenarioResult r = scenarioService.run("catalog-alias-revision");
        assertAllPassed(r);
        String viewId = (String) r.objects.get("viewId");
        ConsumerView view = viewRepo.findById(viewId).orElseThrow();
        assertEquals("SUSPENDED", view.status);
        assertNotNull(view.identityVersions, "历史视图应保留当时身份版本");
    }

    @Test
    void signatureRevocationDowngradesIdentity() {
        ScenarioService.ScenarioResult r = scenarioService.run("signature-revocation");
        assertAllPassed(r);
        String identityId = (String) r.objects.get("identityId");
        ComponentIdentity identity = identityRepo.findById(identityId).orElseThrow();
        assertEquals("INSUFFICIENT_EVIDENCE", identity.status);
        String factAfter = (String) r.objects.get("factIdAfter");
        assertFalse(itemRepo.findByBuildFactId(factAfter).stream()
                .anyMatch(i -> i.snapshot.contains("\"PROVEN\"")), "撤销后新事实不得显示已证实");
    }

    private static void assertAllPassed(ScenarioService.ScenarioResult r) {
        List<String> failed = r.checks.stream()
                .filter(c -> !(Boolean) c.get("passed"))
                .map(c -> c.get("name") + ": " + c.get("detail"))
                .toList();
        assertTrue(failed.isEmpty(), "场景 " + r.scenario + " 存在未通过检查: " + failed);
    }
}
