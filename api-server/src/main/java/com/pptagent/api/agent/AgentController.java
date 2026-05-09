package com.pptagent.api.agent;

import com.pptagent.agent.dto.AgentRequests;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.service.AgentJobService;
import com.pptagent.agent.tool.AgentMcpToolRegistry;
import com.pptagent.api.presentation.PresentationExportService;
import com.pptagent.api.presentation.dto.PresentationExportResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentJobService agentJobService;
    private final AgentMcpToolRegistry toolRegistry;
    private final PresentationExportService presentationExportService;
    private final AgentProgressStreamService progressStreamService;
    private final AgentProgressSnapshotService progressSnapshotService;

    public AgentController(
            AgentJobService agentJobService,
            AgentMcpToolRegistry toolRegistry,
            PresentationExportService presentationExportService,
            AgentProgressStreamService progressStreamService,
            AgentProgressSnapshotService progressSnapshotService
    ) {
        this.agentJobService = agentJobService;
        this.toolRegistry = toolRegistry;
        this.presentationExportService = presentationExportService;
        this.progressStreamService = progressStreamService;
        this.progressSnapshotService = progressSnapshotService;
    }

    @PostMapping("/jobs")
    public ApiResponse<AgentResponse> createJob(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AgentRequests.CreateJobRequest request
    ) {
        return ApiResponse.ok(agentJobService.createJob(user.id(), request));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<AgentResponse> getJob(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.getJob(user.id(), id));
    }

    @GetMapping(value = "/jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        Object snapshot = progressSnapshotService.snapshot(user.id(), id);
        SseEmitter emitter = progressStreamService.subscribe(id);
        progressStreamService.sendSnapshot(emitter, snapshot);
        return emitter;
    }

    @PostMapping("/jobs/{id}/plan")
    public ApiResponse<AgentResponse> plan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.plan(user.id(), id));
    }

    @PostMapping("/jobs/{id}/approve-plan")
    public ApiResponse<AgentResponse> approvePlan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.approvePlan(user.id(), id));
    }

    @PostMapping("/jobs/{id}/execute")
    public ApiResponse<AgentResponse> execute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.execute(user.id(), id));
    }

    @PostMapping("/jobs/{id}/pause")
    public ApiResponse<AgentResponse> pause(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.pause(user.id(), id));
    }

    @PostMapping("/jobs/{id}/resume")
    public ApiResponse<AgentResponse> resume(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.resume(user.id(), id));
    }

    @PostMapping("/jobs/{id}/publish")
    public ApiResponse<PresentationExportResponse> publish(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        Long presentationId = agentJobService.requirePublishableJob(user.id(), id);
        PresentationExportResponse response = presentationExportService.exportPresentation(user.id(), presentationId);
        agentJobService.markPublished(user.id(), id);
        return ApiResponse.ok(response);
    }

    @GetMapping("/jobs/{id}/memory")
    public ApiResponse<List<AgentResponse.MemoryResponse>> memory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.getMemory(user.id(), id));
    }

    @PostMapping("/jobs/{id}/tools/call")
    public ApiResponse<AgentResponse.ToolCallResponse> callTool(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AgentRequests.ToolCallRequest request
    ) {
        return ApiResponse.ok(agentJobService.callTool(user.id(), id, request));
    }

    @GetMapping("/mcp/tools")
    public ApiResponse<List<String>> listMcpTools() {
        return ApiResponse.ok(toolRegistry.toolNames());
    }
}
