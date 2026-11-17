package com.scf.meta.monitor;

import com.scf.meta.buildfact.BuildFactItem;
import com.scf.meta.buildfact.BuildFactItemRepository;
import com.scf.meta.catalog.ConsumerView;
import com.scf.meta.catalog.ConsumerViewRepository;
import com.scf.meta.common.Json;
import com.scf.meta.identity.ComponentIdentity;
import com.scf.meta.review.ReviewService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 变动监测器：目录修订或签名撤销时定位当前引用，
 * 暂停受影响的下游视图；历史视图保留当时的身份版本快照。
 */
@Service
public class MonitorService {

    private final BuildFactItemRepository itemRepo;
    private final ConsumerViewRepository viewRepo;
    private final MonitorEventRepository monitorRepo;
    private final ReviewService reviewService;
    private final Json json;

    public MonitorService(BuildFactItemRepository itemRepo,
                          ConsumerViewRepository viewRepo,
                          MonitorEventRepository monitorRepo,
                          ReviewService reviewService,
                          Json json) {
        this.itemRepo = itemRepo;
        this.viewRepo = viewRepo;
        this.monitorRepo = monitorRepo;
        this.reviewService = reviewService;
        this.json = json;
    }

    /** 定位引用了任一身份的下游视图并暂停，返回受影响视图 id。 */
    @Transactional
    public List<String> suspendViewsReferencing(List<ComponentIdentity> identities,
                                                String reason, String eventType, String actor) {
        if (identities.isEmpty()) return List.of();
        Set<String> identityIds = new LinkedHashSet<>();
        identities.forEach(i -> identityIds.add(i.id));
        Set<String> factIds = new LinkedHashSet<>();
        for (String identityId : identityIds) {
            for (BuildFactItem item : itemRepo.findByItemKindAndRefId("IDENTITY", identityId)) {
                factIds.add(item.buildFactId);
            }
        }
        List<String> affected = new ArrayList<>();
        for (String factId : factIds) {
            for (ConsumerView view : viewRepo.findByBuildFactId(factId)) {
                if ("ACTIVE".equals(view.status)) {
                    view.status = ConsumerView.Status.SUSPENDED.name();
                    view.suspendReason = reason;
                    view.updatedAt = java.time.Instant.now();
                    viewRepo.save(view);
                    affected.add(view.id);
                    reviewService.record("VIEW", view.id, "SUSPENDED", reason, actor);
                }
            }
        }
        MonitorEvent event = new MonitorEvent();
        event.eventType = eventType;
        event.detail = reason + "；定位到 " + factIds.size() + " 个事实、暂停 " + affected.size()
                + " 个下游视图（历史视图保留当时身份版本）";
        event.affectedViews = json.write(affected);
        monitorRepo.save(event);
        return affected;
    }
}
