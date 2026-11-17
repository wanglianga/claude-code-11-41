package com.scf.meta.monitor;

import com.scf.meta.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorEventRepository monitorRepo;

    public MonitorController(MonitorEventRepository monitorRepo) {
        this.monitorRepo = monitorRepo;
    }

    @GetMapping("/events")
    public ApiResponse<List<MonitorEvent>> events(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ApiResponse.ok(monitorRepo.findByEventTypeOrderByCreatedAtDesc(type));
        }
        return ApiResponse.ok(monitorRepo.findAllByOrderByCreatedAtDesc());
    }
}
