package com.scf.meta.signature;

import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.identity.IdentityResolutionService;
import com.scf.meta.monitor.MonitorEvent;
import com.scf.meta.monitor.MonitorService;
import com.scf.meta.review.ReviewService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 签名服务：登记与撤销。撤销触发身份降级与变动监测。 */
@Service
public class SignatureService {

    private final SignatureRecordRepository signatureRepo;
    private final IdentityResolutionService identityService;
    private final MonitorService monitorService;
    private final ReviewService reviewService;

    public SignatureService(SignatureRecordRepository signatureRepo,
                            IdentityResolutionService identityService,
                            MonitorService monitorService,
                            ReviewService reviewService) {
        this.signatureRepo = signatureRepo;
        this.identityService = identityService;
        this.monitorService = monitorService;
        this.reviewService = reviewService;
    }

    @Transactional
    public SignatureRecord revoke(String signatureId, String actor) {
        SignatureRecord record = signatureRepo.findById(signatureId)
                .orElseThrow(() -> new IllegalArgumentException("签名记录不存在: " + signatureId));
        if ("REVOKED".equals(record.status)) {
            return record;
        }
        record.status = SignatureRecord.Status.REVOKED.name();
        record.revokedAt = Instant.now();
        signatureRepo.save(record);
        reviewService.record("IDENTITY", record.id, "SIGNATURE_REVOKED",
                "subjectHash=" + record.subjectHash, actor);

        // 唯一依据为该签名的身份降为证据不足
        List<ComponentIdentity> affected = identityService.handleSignatureRevocation(signatureId, actor);
        // 变动监测：暂停引用受影响身份的下游视图
        monitorService.suspendViewsReferencing(affected,
                "签名 " + signatureId.substring(0, 8) + " 撤销，"
                        + affected.size() + " 个身份降为证据不足，新事实不得再显示已证实",
                MonitorEvent.Type.SIGNATURE_REVOKED.name(), actor);
        return record;
    }
}
