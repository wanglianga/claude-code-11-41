package com.scf.meta.identity;

import com.scf.meta.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identities")
public class IdentityController {

    private final ComponentIdentityRepository identityRepo;
    private final ComponentClaimRepository claimRepo;

    public IdentityController(ComponentIdentityRepository identityRepo,
                              ComponentClaimRepository claimRepo) {
        this.identityRepo = identityRepo;
        this.claimRepo = claimRepo;
    }

    @GetMapping
    public ApiResponse<List<ComponentIdentity>> list(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String name) {
        if (status != null && !status.isBlank()) {
            return ApiResponse.ok(identityRepo.findByStatus(status));
        }
        if (name != null && !name.isBlank()) {
            return ApiResponse.ok(identityRepo.findByName(name));
        }
        return ApiResponse.ok(identityRepo.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        ComponentIdentity identity = identityRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("身份不存在: " + id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identity);
        result.put("claims", claimRepo.findByIdentityId(id));
        return ApiResponse.ok(result);
    }
}
