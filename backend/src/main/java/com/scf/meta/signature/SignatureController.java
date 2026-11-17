package com.scf.meta.signature;

import com.scf.meta.common.ApiResponse;
import com.scf.meta.common.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signatures")
public class SignatureController {

    private final SignatureRecordRepository signatureRepo;
    private final SignatureService signatureService;

    public SignatureController(SignatureRecordRepository signatureRepo,
                               SignatureService signatureService) {
        this.signatureRepo = signatureRepo;
        this.signatureService = signatureService;
    }

    @GetMapping
    public ApiResponse<List<SignatureRecord>> list() {
        return ApiResponse.ok(signatureRepo.findAll());
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<SignatureRecord> revoke(@PathVariable String id, HttpServletRequest http) {
        String actor = (String) http.getAttribute(AuthInterceptor.ATTR_USER);
        return ApiResponse.ok(signatureService.revoke(id, actor));
    }
}
