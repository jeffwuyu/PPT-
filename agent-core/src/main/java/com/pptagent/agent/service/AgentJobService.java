package com.pptagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.agent.ai.AgentAiClient;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.agent.dto.AgentRequests;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.tool.AgentToolResult;
import com.pptagent.agent.tool.AgentToolService;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.agent.AgentArtifactRefEntity;
import com.pptagent.domain.agent.AgentArtifactType;
import com.pptagent.domain.agent.AgentCheckpointEntity;
import com.pptagent.domain.agent.AgentCheckpointStatus;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentJobStatus;
import com.pptagent.domain.agent.AgentMemoryScope;
import com.pptagent.domain.agent.AgentMessageEntity;
import com.pptagent.domain.agent.AgentPlanEntity;
import com.pptagent.domain.agent.AgentPlanRevisionEntity;
import com.pptagent.domain.agent.AgentSourceEntity;
import com.pptagent.domain.agent.AgentSourceType;
import com.pptagent.domain.agent.repository.AgentArtifactRefRepository;
import com.pptagent.domain.agent.repository.AgentCheckpointRepository;
import com.pptagent.domain.agent.repository.AgentJobRepository;
import com.pptagent.domain.agent.repository.AgentMessageRepository;
import com.pptagent.domain.agent.repository.AgentPlanRepository;
import com.pptagent.domain.agent.repository.AgentPlanRevisionRepository;
import com.pptagent.domain.agent.repository.AgentSourceRepository;
import com.pptagent.domain.auth.ProjectMemberEntity;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AgentJobService {

    private final AgentJobRepository jobRepository;
    private final AgentSourceRepository sourceRepository;
    private final AgentPlanRepository planRepository;
    private final AgentCheckpointRepository checkpointRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentArtifactRefRepository artifactRefRepository;
    private final AgentPlanRevisionRepository planRevisionRepository;
    private final PresentationRepository presentationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final FileAssetRepository fileAssetRepository;
    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;
    private final AgentAiClient aiClient;
    private final AgentMemoryService memoryService;
    private final AgentToolService toolService;
    private final AgentPlanParser planParser;
    private final AgentSlideJsonExecutionService slideJsonExecutionService;
    private final AgentResponseMapper responseMapper;
    private final AgentJsonService jsonService;
    private final AgentProperties agentProperties;

    public AgentJobService(
            AgentJobRepository jobRepository,
            AgentSourceRepository sourceRepository,
            AgentPlanRepository planRepository,
            AgentCheckpointRepository checkpointRepository,
            AgentMessageRepository messageRepository,
            AgentArtifactRefRepository artifactRefRepository,
            AgentPlanRevisionRepository planRevisionRepository,
            PresentationRepository presentationRepository,
            ProjectMemberRepository projectMemberRepository,
            FileAssetRepository fileAssetRepository,
            SlideRepository slideRepository,
            SlideElementRepository slideElementRepository,
            AgentAiClient aiClient,
            AgentMemoryService memoryService,
            AgentToolService toolService,
            AgentPlanParser planParser,
            AgentSlideJsonExecutionService slideJsonExecutionService,
            AgentResponseMapper responseMapper,
            AgentJsonService jsonService,
            AgentProperties agentProperties
    ) {
        this.jobRepository = jobRepository;
        this.sourceRepository = sourceRepository;
        this.planRepository = planRepository;
        this.checkpointRepository = checkpointRepository;
        this.messageRepository = messageRepository;
        this.artifactRefRepository = artifactRefRepository;
        this.planRevisionRepository = planRevisionRepository;
        this.presentationRepository = presentationRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
        this.aiClient = aiClient;
        this.memoryService = memoryService;
        this.toolService = toolService;
        this.planParser = planParser;
        this.slideJsonExecutionService = slideJsonExecutionService;
        this.responseMapper = responseMapper;
        this.jsonService = jsonService;
        this.agentProperties = agentProperties;
    }

    @Transactional
    public AgentResponse createJob(Long userId, AgentRequests.CreateJobRequest request) {
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent prompt is required");
        }
        requireEditablePresentation(userId, request.presentationId());
        AgentJobEntity job = new AgentJobEntity(userId, request.presentationId(), prompt);
        jobRepository.insert(job);
        sourceRepository.insert(new AgentSourceEntity(job.getId(), AgentSourceType.PROMPT, null, prompt, jsonService.emptyObject()));
        insertFileSources(userId, job.getId(), request.fileAssetIds(), AgentSourceType.FILE);
        insertFileSources(userId, job.getId(), request.imageAssetIds(), AgentSourceType.IMAGE);
        memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "USER_PROMPT", prompt);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse plan(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        job.changeStatus(AgentJobStatus.ANALYZING);
        jobRepository.updateById(job);

        String memory = memoryService.buildMemoryContext(job.getId());
        String markdown = aiClient.generatePlan(buildPlanningPrompt(job), memory);
        AgentPlanEntity plan = new AgentPlanEntity(job.getId(), markdown, planJson(markdown), nextPlanVersion(job.getId()));
        planRepository.insert(plan);
        messageRepository.insert(new AgentMessageEntity(job.getId(), null, "ASSISTANT", markdown, jsonService.emptyObject()));
        memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.LONG_TERM, "PLAN_SUMMARY", firstLine(markdown));

        job.changeStatus(AgentJobStatus.PLANNED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse approvePlan(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        AgentPlanEntity plan = latestPlan(job.getId());
        if (!checkpointRepository.existsForJob(job.getId())) {
            List<AgentPlanParser.ParsedCheckpoint> parsed = planParser.parse(plan.getPlanMarkdown(), agentProperties.maxPlanSlides());
            int order = 1;
            for (AgentPlanParser.ParsedCheckpoint checkpoint : parsed) {
                checkpointRepository.insert(new AgentCheckpointEntity(
                        job.getId(),
                        plan.getId(),
                        order++,
                        checkpoint.title(),
                        checkpoint.objective(),
                        agentProperties.defaultCheckpointMaxIterations()
                ));
            }
        }
        job.changeStatus(AgentJobStatus.PLANNED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse execute(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        latestPlan(job.getId());
        if (!checkpointRepository.existsForJob(job.getId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent plan must be approved before execution");
        }
        job.changeStatus(AgentJobStatus.EXECUTING);
        jobRepository.updateById(job);

        for (AgentCheckpointEntity checkpoint : checkpointRepository.findByJobIdOrderByCheckpointOrder(job.getId())) {
            if (checkpoint.getStatus() == AgentCheckpointStatus.COMPLETED || checkpoint.getStatus() == AgentCheckpointStatus.CANCELLED) {
                continue;
            }
            executeCheckpoint(userId, job, checkpoint);
            if (checkpoint.getStatus() == AgentCheckpointStatus.FAILED) {
                job.fail(checkpoint.getFailureReason());
                jobRepository.updateById(job);
                return buildResponse(job);
            }
        }
        job.changeStatus(AgentJobStatus.GENERATED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse pause(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        job.changeStatus(AgentJobStatus.PAUSED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse resume(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (job.getStatus() == AgentJobStatus.PAUSED) {
            job.changeStatus(AgentJobStatus.EXECUTING);
            jobRepository.updateById(job);
        }
        return buildResponse(job);
    }

    @Transactional(readOnly = true)
    public AgentResponse getJob(Long userId, Long jobId) {
        return buildResponse(loadReadableJob(userId, jobId));
    }

    @Transactional(readOnly = true)
    public List<AgentResponse.MemoryResponse> getMemory(Long userId, Long jobId) {
        AgentJobEntity job = loadReadableJob(userId, jobId);
        return memoryService.list(job.getId()).stream().map(responseMapper::toMemoryResponse).toList();
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public AgentResponse.ToolCallResponse callTool(Long userId, Long jobId, AgentRequests.ToolCallRequest request) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        AgentToolResult result = toolService.callTool(userId, job.getId(), null, request.toolName(), request.arguments());
        memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "TOOL_RESULT", result.response().toString());
        return new AgentResponse.ToolCallResponse(result.toolCallId(), result.toolName(), com.pptagent.domain.agent.AgentToolCallStatus.SUCCESS, result.response(), null);
    }

    private void executeCheckpoint(Long userId, AgentJobEntity job, AgentCheckpointEntity checkpoint) {
        boolean forceReplan = checkpoint.getObjective().toLowerCase(Locale.ROOT).contains("replan")
                || checkpoint.getObjective().contains("重新设计")
                || job.getPromptText().toLowerCase(Locale.ROOT).contains("force_replan");

        while (checkpoint.getCurrentIterations() < checkpoint.getMaxIterations()) {
            checkpoint.startNextIteration();
            checkpointRepository.updateById(checkpoint);

            String react = aiClient.reactCheckpoint(checkpoint.getObjective(), memoryService.buildMemoryContext(job.getId()), forceReplan);
            messageRepository.insert(new AgentMessageEntity(job.getId(), checkpoint.getId(), "ASSISTANT", react, jsonService.emptyObject()));

            if (forceReplan) {
                Long slideId = slideJsonExecutionService.createSlideArtifact(job, checkpoint, checkpoint.getCurrentIterations(), "Cancelled checkpoint draft");
                revisePlanForCancelledCheckpoint(job, checkpoint, "React validation cancelled checkpoint " + checkpoint.getCheckpointOrder());
                rollbackArtifacts(checkpoint.getId());
                checkpoint.cancel("Cancelled by React replanning");
                checkpointRepository.updateById(checkpoint);
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "REPLAN", "Cancelled checkpoint and rolled back slide " + slideId);
                return;
            }

            try {
                toolService.callTool(userId, job.getId(), checkpoint.getId(), "bailian_image_generate", imageToolArguments(checkpoint));
                slideJsonExecutionService.createSlideArtifact(job, checkpoint, checkpoint.getCurrentIterations(), checkpoint.getTitle());
                checkpoint.complete();
                checkpointRepository.updateById(checkpoint);
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "CHECKPOINT", "Completed " + checkpoint.getTitle());
                return;
            } catch (AgentSlideJsonExecutionService.SlideValidationException exception) {
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "SLIDE_VALIDATION_RETRY", exception.getMessage());
                if (checkpoint.getCurrentIterations() >= checkpoint.getMaxIterations()) {
                    checkpoint.fail(exception.getMessage());
                    checkpointRepository.updateById(checkpoint);
                    return;
                }
            }
        }

        checkpoint.fail("Checkpoint reached max iteration limit");
        checkpointRepository.updateById(checkpoint);
    }

    private void rollbackArtifacts(Long checkpointId) {
        for (AgentArtifactRefEntity ref : artifactRefRepository.findActiveByCheckpoint(checkpointId)) {
            if (ref.getArtifactType() == AgentArtifactType.ELEMENT) {
                SlideElementEntity element = slideElementRepository.selectById(ref.getArtifactId());
                if (element != null && "0".equals(element.getIsDeleted())) {
                    element.markDeleted();
                    slideElementRepository.updateById(element);
                }
            } else if (ref.getArtifactType() == AgentArtifactType.SLIDE) {
                SlideEntity slide = slideRepository.selectById(ref.getArtifactId());
                if (slide != null && "0".equals(slide.getIsDeleted())) {
                    slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()).forEach(element -> {
                        element.markDeleted();
                        slideElementRepository.updateById(element);
                    });
                    slide.markDeleted();
                    slideRepository.updateById(slide);
                }
            }
            ref.deactivate("ROLLBACK");
            artifactRefRepository.updateById(ref);
        }
    }

    private void revisePlanForCancelledCheckpoint(AgentJobEntity job, AgentCheckpointEntity checkpoint, String reason) {
        AgentPlanEntity current = latestPlan(job.getId());
        String revisedMarkdown = current.getPlanMarkdown() + "\n\n> Revision: checkpoint %d cancelled because %s.\n".formatted(checkpoint.getCheckpointOrder(), reason);
        AgentPlanEntity revised = new AgentPlanEntity(job.getId(), revisedMarkdown, planJson(revisedMarkdown), nextPlanVersion(job.getId()));
        planRepository.insert(revised);
        planRevisionRepository.insert(new AgentPlanRevisionEntity(job.getId(), current.getId(), revised.getId(), reason, checkpoint.getId()));
    }

    private JsonNode imageToolArguments(AgentCheckpointEntity checkpoint) {
        ObjectNode arguments = jsonService.objectNode();
        arguments.put("prompt", "Professional PPT illustration for: " + checkpoint.getObjective());
        arguments.put("style", "flat illustration");
        return arguments;
    }

    private String buildPlanningPrompt(AgentJobEntity job) {
        return """
                User request:
                %s

                Available source count: %d
                Return a Markdown checklist plan only.
                """.formatted(job.getPromptText(), sourceRepository.findByJobId(job.getId()).size());
    }

    private void insertFileSources(Long userId, Long jobId, List<Long> fileIds, AgentSourceType sourceType) {
        if (fileIds == null) {
            return;
        }
        for (Long fileId : fileIds) {
            FileAssetEntity asset = fileAssetRepository.findWithOwnerById(fileId)
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent source file not found"));
            if (!asset.getOwnerUserId().equals(userId)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "Agent source file does not belong to current user");
            }
            if (sourceType == AgentSourceType.IMAGE && asset.getFileType() != FileType.USER_IMAGE && asset.getFileType() != FileType.CRAWLED_IMAGE) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent image source must be an image file");
            }
            sourceRepository.insert(new AgentSourceEntity(jobId, sourceType, fileId, null, jsonService.emptyObject()));
        }
    }

    private AgentJobEntity loadEditableJob(Long userId, Long jobId) {
        AgentJobEntity job = loadReadableJob(userId, jobId);
        requireEditablePresentation(userId, job.getPresentationId());
        return job;
    }

    private AgentJobEntity loadReadableJob(Long userId, Long jobId) {
        AgentJobEntity job = jobRepository.selectById(jobId);
        if (job == null) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Agent job not found");
        }
        requireReadablePresentation(userId, job.getPresentationId());
        return job;
    }

    private PresentationEntity requireReadablePresentation(Long userId, Long presentationId) {
        PresentationEntity presentation = presentationRepository.selectById(presentationId);
        if (presentation == null || !"0".equals(presentation.getIsDeleted())) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Presentation not found");
        }
        if (!presentation.getOwnerUserId().equals(userId) && projectMemberRepository.findByProjectIdAndUserId(presentationId, userId).isEmpty()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No access to presentation");
        }
        return presentation;
    }

    private PresentationEntity requireEditablePresentation(Long userId, Long presentationId) {
        PresentationEntity presentation = requireReadablePresentation(userId, presentationId);
        if (presentation.getOwnerUserId().equals(userId)) {
            return presentation;
        }
        ProjectMemberEntity member = projectMemberRepository.findByProjectIdAndUserId(presentationId, userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "No access to presentation"));
        if (!member.canEdit()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No edit access to presentation");
        }
        return presentation;
    }

    private AgentPlanEntity latestPlan(Long jobId) {
        return planRepository.findLatestByJobId(jobId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "Agent plan has not been generated"));
    }

    private int nextPlanVersion(Long jobId) {
        return planRepository.findLatestByJobId(jobId).map(plan -> plan.getVersionNumber() + 1).orElse(1);
    }

    private String planJson(String markdown) {
        ObjectNode node = jsonService.objectNode();
        node.put("format", "markdown");
        node.put("length", markdown == null ? 0 : markdown.length());
        return jsonService.toJson(node);
    }

    private AgentResponse buildResponse(AgentJobEntity job) {
        AgentPlanEntity plan = planRepository.findLatestByJobId(job.getId()).orElse(null);
        return responseMapper.toJobResponse(job, plan, checkpointRepository.findByJobIdOrderByCheckpointOrder(job.getId()));
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
    }

}
