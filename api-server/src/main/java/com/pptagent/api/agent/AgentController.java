package com.pptagent.api.agent;

import com.pptagent.agent.dto.AgentRequests;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.service.AgentJobService;
import com.pptagent.agent.tool.AgentMcpToolRegistry;
import com.pptagent.api.presentation.PresentationExportService;
import com.pptagent.api.presentation.dto.PresentationExportResponse;
import com.pptagent.api.agent.dto.GenerationJobResponse;
import com.pptagent.api.agent.dto.GenerationSubmissionRequest;
import com.pptagent.api.agent.dto.SourceParseRequest;
import com.pptagent.api.agent.dto.SourceParseResponse;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final AgentGenerationService generationService;
    private final SourceParseService sourceParseService;

    public AgentController(
            AgentJobService agentJobService,
            AgentMcpToolRegistry toolRegistry,
            PresentationExportService presentationExportService,
            AgentProgressStreamService progressStreamService,
            AgentProgressSnapshotService progressSnapshotService,
            AgentGenerationService generationService,
            SourceParseService sourceParseService
    ) {
        this.agentJobService = agentJobService;
        this.toolRegistry = toolRegistry;
        this.presentationExportService = presentationExportService;
        this.progressStreamService = progressStreamService;
        this.progressSnapshotService = progressSnapshotService;
        this.generationService = generationService;
        this.sourceParseService = sourceParseService;
    }

    @PostMapping("/sources/parse")
    public ResponseEntity<ApiResponse<SourceParseResponse>> parseSource(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SourceParseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(sourceParseService.submit(user.id(), request)));
    }

    @GetMapping("/sources/parse-jobs/{parseJobId}")
    public ApiResponse<SourceParseResponse> getSourceParse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long parseJobId
    ) {
        return ApiResponse.ok(sourceParseService.get(user.id(), parseJobId));
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
            @PathVariable Long id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        long watermark = progressStreamService.currentWatermark();
        Object snapshot = progressSnapshotService.snapshot(user.id(), id);
        SseEmitter emitter = progressStreamService.subscribe(id);
        progressStreamService.sendSnapshot(emitter, java.util.Map.of("watermark", watermark, "state", snapshot));
        try {
            String cursor = lastEventId == null || lastEventId.isBlank() ? String.valueOf(watermark) : lastEventId;
            for (var event : progressStreamService.replayAfter(id, cursor)) {
                progressStreamService.sendReplay(emitter, event);
            }
        } catch (CursorExpiredException exception) {
            progressStreamService.sendCursorExpired(emitter, exception.getMessage());
            // A stale cursor must be recoverable in one connection: return a
            // fresh state plus its new watermark after the explicit signal.
            progressStreamService.sendSnapshot(emitter, java.util.Map.of(
                    "watermark", progressStreamService.currentWatermark(),
                    "state", progressSnapshotService.snapshot(user.id(), id)));
        }
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

    @GetMapping("/jobs/{id}/outline")
    public ApiResponse<AgentResponse.OutlineResponse> getOutline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.getOutline(user.id(), id));
    }

    @PutMapping("/jobs/{id}/outline")
    public ApiResponse<AgentResponse.OutlineResponse> updateOutline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AgentRequests.UpdateOutlineRequest request
    ) {
        return ApiResponse.ok(agentJobService.updateOutline(user.id(), id, request));
    }

    @PostMapping("/jobs/{id}/execute")
    public ApiResponse<AgentResponse> execute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentJobService.execute(user.id(), id));
    }

    @PostMapping("/jobs/{id}/generations")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> submitGeneration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody GenerationSubmissionRequest request
    ) {
        GenerationJobResponse response = generationService.submit(user.id(), id, request);
        return ResponseEntity.accepted().body(ApiResponse.ok(response));
    }

    @GetMapping("/generations/{generationJobId}")
    public ApiResponse<GenerationJobResponse> getGeneration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long generationJobId
    ) {
        return ApiResponse.ok(generationService.get(user.id(), generationJobId));
    }

    @PostMapping("/generations/{generationJobId}/pause")
    public ApiResponse<GenerationJobResponse> pauseGeneration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long generationJobId
    ) {
        return ApiResponse.ok(generationService.pause(user.id(), generationJobId));
    }

    @PostMapping("/generations/{generationJobId}/resume")
    public ApiResponse<GenerationJobResponse> resumeGeneration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long generationJobId
    ) {
        return ApiResponse.ok(generationService.resume(user.id(), generationJobId));
    }

    @PostMapping("/generations/{generationJobId}/cancel")
    public ApiResponse<GenerationJobResponse> cancelGeneration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long generationJobId
    ) {
        return ApiResponse.ok(generationService.cancel(user.id(), generationJobId));
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
