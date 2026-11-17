package com.scf.meta.dependency;

import com.scf.meta.common.ApiResponse;
import com.scf.meta.common.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DependencyController {

    private final CompilerService compilerService;
    private final DependencyEdgeRepository edgeRepo;
    private final CompileRunRepository runRepo;

    public DependencyController(CompilerService compilerService,
                                DependencyEdgeRepository edgeRepo,
                                CompileRunRepository runRepo) {
        this.compilerService = compilerService;
        this.edgeRepo = edgeRepo;
        this.runRepo = runRepo;
    }

    @PostMapping("/compile/{buildId}")
    public ApiResponse<CompilerService.CompileResult> compile(@PathVariable String buildId,
                                                              HttpServletRequest http) {
        String actor = (String) http.getAttribute(AuthInterceptor.ATTR_USER);
        return ApiResponse.ok(compilerService.compile(buildId, null, actor));
    }

    @GetMapping("/edges")
    public ApiResponse<List<DependencyEdge>> edges(@RequestParam String buildId,
                                                   @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ApiResponse.ok(edgeRepo.findByBuildIdAndEdgeStatus(buildId, status));
        }
        return ApiResponse.ok(edgeRepo.findByBuildId(buildId));
    }

    @GetMapping("/compile-runs")
    public ApiResponse<List<CompileRun>> runs(@RequestParam String buildId) {
        return ApiResponse.ok(runRepo.findByBuildIdOrderByStartedAtDesc(buildId));
    }
}
