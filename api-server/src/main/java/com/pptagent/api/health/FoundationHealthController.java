package com.pptagent.api.health;

import com.pptagent.common.api.ApiResponse;
import com.pptagent.common.module.ModuleNames;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class FoundationHealthController {

    private final Environment environment;

    public FoundationHealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/api/health")
    public ApiResponse<FoundationHealth> health() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return ApiResponse.ok(new FoundationHealth("UP", profiles, ModuleNames.FOUNDATION_MODULES));
    }

    public record FoundationHealth(String status, List<String> activeProfiles, List<String> modules) {
    }
}
