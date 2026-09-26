package com.pptagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pptagent.agent.ai.AgentAiClient;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.agent.dto.AgentRequests;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.progress.AgentProgressEvent;
import com.pptagent.agent.progress.AgentProgressPublisher;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    private final AgentTextExtractionService textExtractionService;
    private final AgentResponseMapper responseMapper;
    private final AgentJsonService jsonService;
    private final AgentProperties agentProperties;
    private final AgentProgressPublisher progressPublisher;

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
            AgentTextExtractionService textExtractionService,
            AgentResponseMapper responseMapper,
            AgentJsonService jsonService,
            AgentProperties agentProperties,
            AgentProgressPublisher progressPublisher
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
        this.textExtractionService = textExtractionService;
        this.responseMapper = responseMapper;
        this.jsonService = jsonService;
        this.agentProperties = agentProperties;
        this.progressPublisher = progressPublisher;
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
        for (AgentSourceEntity source : sourceRepository.findByJobId(job.getId())) {
            if (source.getSourceType() == AgentSourceType.FILE && source.getContentText() != null && !source.getContentText().isBlank()) {
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.LONG_TERM, "SOURCE_DOCUMENT", abbreviate(source.getContentText(), 3000));
            }
        }
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse plan(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        job.changeStatus(AgentJobStatus.ANALYZING);
        jobRepository.updateById(job);

        String memory = memoryService.buildMemoryContext(job.getId());
        String markdown = aiClient.generatePlan(buildPlanningPrompt(job), memory);
        String translatedMarkdown = aiClient.translatePlan(markdown);
        AgentPlanEntity plan = new AgentPlanEntity(job.getId(), markdown, planJson(markdown, translatedMarkdown), nextPlanVersion(job.getId()));
        plan.initializeOutline(outlineJson(job.getId(), markdown));
        planRepository.insert(plan);
        messageRepository.insert(new AgentMessageEntity(job.getId(), null, "ASSISTANT", markdown, jsonService.emptyObject()));
        messageRepository.insert(new AgentMessageEntity(job.getId(), null, "ASSISTANT_TRANSLATION", translatedMarkdown, jsonService.emptyObject()));
        memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.LONG_TERM, "PLAN_SUMMARY", firstLine(markdown));

        job.changeStatus(AgentJobStatus.PLANNED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse approvePlan(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (job.getStatus() == AgentJobStatus.EXECUTING || job.getStatus() == AgentJobStatus.GENERATED
                || job.getStatus() == AgentJobStatus.PUBLISHED) {
            throw new PlatformException(ErrorCode.CONFLICT, "Approved outline is immutable after generation starts");
        }
        AgentPlanEntity plan = latestPlan(job.getId());
        validateOutline(job.getId(), jsonService.fromJson(plan.getOutlineJson()));
        plan.approveOutline(sha256(plan.getOutlineJson()));
        planRepository.updateById(plan);
        ensureCheckpointsFromApprovedOutline(job, plan);
        job.changeStatus(AgentJobStatus.PLANNED);
        jobRepository.updateById(job);
        return buildResponse(job);
    }

    @Transactional(readOnly = true)
    public AgentResponse.OutlineResponse getOutline(Long userId, Long jobId) {
        AgentJobEntity job = loadReadableJob(userId, jobId);
        AgentPlanEntity plan = latestPlan(job.getId());
        return outlineResponse(plan);
    }

    @Transactional
    public AgentResponse.OutlineResponse updateOutline(Long userId, Long jobId, AgentRequests.UpdateOutlineRequest request) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (job.getStatus() == AgentJobStatus.EXECUTING || job.getStatus() == AgentJobStatus.GENERATED
                || job.getStatus() == AgentJobStatus.PUBLISHED) {
            throw new PlatformException(ErrorCode.CONFLICT, "Outline cannot be edited after generation starts");
        }
        AgentPlanEntity plan = latestPlan(job.getId());
        if (checkpointRepository.existsForJob(job.getId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Outline is immutable after it has been approved for execution");
        }
        validateOutline(job.getId(), request.outline());
        int updated = planRepository.updateOutlineIfRevision(plan.getId(), request.expectedRevision(), request.outline().toString());
        if (updated != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Outline revision is stale");
        }
        planRevisionRepository.insert(new AgentPlanRevisionEntity(
                job.getId(), plan.getId(), plan.getId(), "OUTLINE_EDIT", null));
        AgentPlanEntity current = latestPlan(job.getId());
        if (Boolean.TRUE.equals(request.approve())) {
            current.approveOutline(sha256(current.getOutlineJson()));
            planRepository.updateById(current);
            ensureCheckpointsFromApprovedOutline(job, current);
        }
        return outlineResponse(latestPlan(job.getId()));
    }

    @Transactional
    public AgentResponse execute(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        AgentPlanEntity approvedPlan = latestPlan(job.getId());
        if (approvedPlan.getApprovedOutlineRevision() == null
                || approvedPlan.getApprovedOutlineRevision() != approvedPlan.getOutlineRevision()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Current outline revision must be approved before execution");
        }
        if (!checkpointRepository.existsForJob(job.getId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent plan must be approved before execution");
        }
        if (jobRepository.startLegacyExecution(jobId) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent job is already executing or has an active generation");
        }
        job.changeStatus(AgentJobStatus.EXECUTING);
        publishJobEvent("checkpoint", job, null, "Agent execution started");

        for (AgentCheckpointEntity checkpoint : checkpointRepository.findByJobIdOrderByCheckpointOrder(job.getId())) {
            if (checkpoint.getStatus() == AgentCheckpointStatus.COMPLETED || checkpoint.getStatus() == AgentCheckpointStatus.CANCELLED) {
                continue;
            }
            executeCheckpoint(userId, job, checkpoint);
            if (checkpoint.getStatus() == AgentCheckpointStatus.FAILED) {
                job.fail(checkpoint.getFailureReason());
                jobRepository.updateById(job);
                publishJobEvent("error", job, checkpoint, checkpoint.getFailureReason());
                return buildResponse(job);
            }
        }
        job.changeStatus(AgentJobStatus.GENERATED);
        jobRepository.updateById(job);
        publishJobEvent("done", job, null, "Agent execution completed");
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse executeGenerationPage(Long userId, Long jobId, Long generationJobId, int pageOrder) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (!generationJobId.equals(job.getActiveGenerationJobId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Generation no longer owns the Agent job");
        }
        AgentPlanEntity plan = latestPlan(jobId);
        if (plan.getApprovedOutlineRevision() == null
                || !plan.getApprovedOutlineRevision().equals(plan.getOutlineRevision())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Approved outline changed before page execution");
        }
        List<AgentCheckpointEntity> checkpoints = checkpointRepository.findByJobIdOrderByCheckpointOrder(jobId);
        if (pageOrder < 1 || pageOrder > checkpoints.size()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Generation page has no matching checkpoint");
        }
        AgentCheckpointEntity checkpoint = checkpoints.get(pageOrder - 1);
        if (checkpoint.getStatus() == AgentCheckpointStatus.COMPLETED) return buildResponse(job);
        if (job.getStatus() != AgentJobStatus.EXECUTING) {
            if (jobRepository.startGeneration(jobId, generationJobId) != 1) {
                throw new PlatformException(ErrorCode.CONFLICT, "Generation no longer owns the Agent job");
            }
            job.changeStatus(AgentJobStatus.EXECUTING);
        }
        executeCheckpoint(userId, job, checkpoint);
        if (checkpoint.getStatus() == AgentCheckpointStatus.FAILED) {
            job.fail(checkpoint.getFailureReason());
            jobRepository.updateById(job);
            publishJobEvent("error", job, checkpoint, checkpoint.getFailureReason());
        }
        return buildResponse(job);
    }

    @Transactional
    public AgentResponse completeGeneration(Long userId, Long jobId, Long generationJobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (!generationJobId.equals(job.getActiveGenerationJobId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Generation no longer owns the Agent job");
        }
        if (job.getStatus() == AgentJobStatus.FAILED) return buildResponse(job);
        boolean incomplete = checkpointRepository.findByJobIdOrderByCheckpointOrder(jobId).stream()
                .anyMatch(checkpoint -> checkpoint.getStatus() != AgentCheckpointStatus.COMPLETED
                        && checkpoint.getStatus() != AgentCheckpointStatus.CANCELLED);
        if (incomplete) throw new PlatformException(ErrorCode.CONFLICT, "Generation still has incomplete pages");
        job.changeStatus(AgentJobStatus.GENERATED);
        jobRepository.updateById(job);
        publishJobEvent("done", job, null, "Agent execution completed");
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
    public Long requirePublishableJob(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (job.getStatus() != AgentJobStatus.GENERATED) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent job must be generated before publishing");
        }
        return job.getPresentationId();
    }

    @Transactional
    public AgentResponse markPublished(Long userId, Long jobId) {
        AgentJobEntity job = loadEditableJob(userId, jobId);
        if (job.getStatus() != AgentJobStatus.GENERATED && job.getStatus() != AgentJobStatus.PUBLISHED) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent job must be generated before publishing");
        }
        job.changeStatus(AgentJobStatus.PUBLISHED);
        jobRepository.updateById(job);
        return buildResponse(job);
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
            publishJobEvent("checkpoint", job, checkpoint, "Checkpoint iteration started");

            String react = aiClient.reactCheckpoint(checkpoint.getObjective(), memoryService.buildMemoryContext(job.getId()), forceReplan);
            AgentMessageEntity reactMessage = new AgentMessageEntity(job.getId(), checkpoint.getId(), "ASSISTANT", react, jsonService.emptyObject());
            messageRepository.insert(reactMessage);
            publishMessageEvent(job, checkpoint, reactMessage);

            if (forceReplan) {
                Long slideId = slideJsonExecutionService.createSlideArtifact(job, checkpoint, checkpoint.getCurrentIterations(), "Cancelled checkpoint draft");
                publishArtifactEvent(job, checkpoint, "SLIDE", slideId, "CREATE");
                revisePlanForCancelledCheckpoint(job, checkpoint, "React validation cancelled checkpoint " + checkpoint.getCheckpointOrder());
                rollbackArtifacts(checkpoint.getId());
                checkpoint.cancel("Cancelled by React replanning");
                checkpointRepository.updateById(checkpoint);
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "REPLAN", "Cancelled checkpoint and rolled back slide " + slideId);
                publishJobEvent("checkpoint", job, checkpoint, "Checkpoint cancelled after React replanning");
                return;
            }

            try {
                AgentToolResult toolResult = toolService.callTool(userId, job.getId(), checkpoint.getId(), "bailian_image_generate", imageToolArguments(checkpoint));
                publishToolEvent(job, checkpoint, toolResult);
                Long slideId = slideJsonExecutionService.createSlideArtifact(
                        job, checkpoint, checkpoint.getCurrentIterations(), checkpoint.getTitle(), approvedPage(job.getId(), checkpoint.getCheckpointOrder()));
                publishArtifactEvent(job, checkpoint, "SLIDE", slideId, "CREATE");
                checkpoint.complete();
                checkpointRepository.updateById(checkpoint);
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "CHECKPOINT", "Completed " + checkpoint.getTitle());
                publishJobEvent("checkpoint", job, checkpoint, "Checkpoint completed");
                return;
            } catch (AgentSlideJsonExecutionService.SlideValidationException exception) {
                memoryService.append(job.getId(), job.getPresentationId(), userId, AgentMemoryScope.SHORT_TERM, "SLIDE_VALIDATION_RETRY", exception.getMessage());
                if (checkpoint.getCurrentIterations() >= checkpoint.getMaxIterations()) {
                    checkpoint.fail(exception.getMessage());
                    checkpointRepository.updateById(checkpoint);
                    publishJobEvent("checkpoint", job, checkpoint, "Checkpoint failed validation");
                    return;
                }
                publishJobEvent("checkpoint", job, checkpoint, "Checkpoint validation retry: " + exception.getMessage());
            }
        }

        checkpoint.fail("Checkpoint reached max iteration limit");
        checkpointRepository.updateById(checkpoint);
        publishJobEvent("checkpoint", job, checkpoint, "Checkpoint reached max iteration limit");
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
        String translatedMarkdown = aiClient.translatePlan(revisedMarkdown);
        AgentPlanEntity revised = new AgentPlanEntity(job.getId(), revisedMarkdown, planJson(revisedMarkdown, translatedMarkdown), nextPlanVersion(job.getId()));
        planRepository.insert(revised);
        planRevisionRepository.insert(new AgentPlanRevisionEntity(job.getId(), current.getId(), revised.getId(), reason, checkpoint.getId()));
    }

    private JsonNode imageToolArguments(AgentCheckpointEntity checkpoint) {
        ObjectNode arguments = jsonService.objectNode();
        arguments.put("prompt", "Professional PPT illustration for: " + checkpoint.getObjective());
        arguments.put("style", "flat illustration");
        return arguments;
    }

    private JsonNode approvedPage(Long jobId, int checkpointOrder) {
        AgentPlanEntity plan = latestPlan(jobId);
        if (plan.getApprovedOutlineRevision() == null
                || plan.getApprovedOutlineRevision() != plan.getOutlineRevision()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Approved outline changed before page generation");
        }
        JsonNode pages = jsonService.fromJson(plan.getApprovedOutlineJson()).path("pages");
        if (!pages.isArray() || checkpointOrder < 1 || checkpointOrder > pages.size()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Checkpoint has no matching approved outline page");
        }
        return pages.get(checkpointOrder - 1);
    }

    private String buildPlanningPrompt(AgentJobEntity job) {
        return """
                User request:
                %s

                Available source count: %d

                Source excerpts:
                %s
                Return a Markdown checklist plan only.
                """.formatted(job.getPromptText(), sourceRepository.findByJobId(job.getId()).size(), sourceExcerpt(job.getId()));
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
            String contentText = null;
            String metadataJson = jsonService.emptyObject();
            if (sourceType == AgentSourceType.IMAGE && asset.getFileType() != FileType.USER_IMAGE && asset.getFileType() != FileType.CRAWLED_IMAGE) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent image source must be an image file");
            }
            if (sourceType == AgentSourceType.FILE) {
                if (asset.getFileType() != FileType.SOURCE_TEXT) {
                    throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent source file must be SOURCE_TEXT");
                }
                AgentTextExtractionService.ExtractedDocument extracted = textExtractionService.extractStructured(asset);
                contentText = extracted.blocks().stream().map(AgentTextExtractionService.SourceBlock::text).reduce((a, b) -> a + "\n" + b).orElse("");
                metadataJson = jsonService.toJson(java.util.Map.of(
                        "sourceHash", extracted.sourceHash(),
                        "parserVersion", extracted.parserVersion(),
                        "blocks", extracted.blocks(),
                        "warnings", extracted.warnings()
                ));
            }
            sourceRepository.insert(new AgentSourceEntity(jobId, sourceType, fileId, contentText, metadataJson));
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

    private String planJson(String markdown, String translatedMarkdown) {
        ObjectNode node = jsonService.objectNode();
        node.put("format", "markdown");
        node.put("length", markdown == null ? 0 : markdown.length());
        node.put("translatedMarkdown", translatedMarkdown == null ? "" : translatedMarkdown);
        node.put("translatedLength", translatedMarkdown == null ? 0 : translatedMarkdown.length());
        return jsonService.toJson(node);
    }

    private String outlineJson(Long jobId, String markdown) {
        ObjectNode outline = jsonService.objectNode();
        ArrayNode pages = outline.putArray("pages");
        int order = 1;
        for (AgentPlanParser.ParsedCheckpoint checkpoint : planParser.parse(markdown, agentProperties.maxPlanSlides())) {
            ObjectNode page = pages.addObject();
            page.put("pageId", "job-%d-page-%d".formatted(jobId, order));
            page.put("order", order++);
            page.put("title", checkpoint.title());
            page.put("objective", checkpoint.objective());
            page.putArray("keyPoints").add(checkpoint.objective());
            page.putArray("sourceBlockIds");
        }
        return jsonService.toJson(outline);
    }

    private void validateOutline(Long jobId, JsonNode outline) {
        if (outline == null || !outline.isObject() || !outline.path("pages").isArray()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline must contain a pages array");
        }
        java.util.Set<String> pageIds = new java.util.HashSet<>();
        int order = 1;
        java.util.Set<String> availableSourceBlocks = availableSourceBlocks(jobId);
        if (outline.path("pages").size() < 1 || outline.path("pages").size() > agentProperties.maxPlanSlides()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline page count is outside the allowed range");
        }
        for (JsonNode page : outline.path("pages")) {
            String pageId = page.path("pageId").asText("").trim();
            if (pageId.isBlank() || !pageIds.add(pageId)) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline pageId must be present and unique");
            }
            if (page.path("title").asText("").isBlank() || page.path("objective").asText("").isBlank()) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline page title and objective are required");
            }
            if (!page.path("keyPoints").isArray() || page.path("keyPoints").size() == 0) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline keyPoints are required");
            }
            if (!page.path("sourceBlockIds").isArray()) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline sourceBlockIds must be an array");
            }
            for (JsonNode sourceId : page.path("sourceBlockIds")) {
                if (!availableSourceBlocks.contains(sourceId.asText())) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "Outline references an unavailable source block");
                }
            }
            if (page.has("order") && page.path("order").asInt(order) != order) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "Outline page order must be contiguous");
            }
            order++;
        }
    }

    private java.util.Set<String> availableSourceBlocks(Long jobId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (AgentSourceEntity source : sourceRepository.findByJobId(jobId)) {
            if (source.getSourceType() != AgentSourceType.FILE) continue;
            JsonNode blocks = jsonService.fromJson(source.getMetadataJson()).path("blocks");
            if (!blocks.isArray()) continue;
            blocks.forEach(block -> {
                String id = block.path("blockId").asText("");
                if (!id.isBlank()) ids.add(id);
            });
        }
        return ids;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private void ensureCheckpointsFromApprovedOutline(AgentJobEntity job, AgentPlanEntity plan) {
        if (checkpointRepository.existsForJob(job.getId())) {
            return;
        }
        JsonNode approvedPages = jsonService.fromJson(plan.getApprovedOutlineJson()).path("pages");
        int order = 1;
        for (JsonNode page : approvedPages) {
            String title = page.path("title").asText();
            String objective = page.path("objective").asText();
            String keyPoints = page.path("keyPoints").isArray()
                    ? java.util.stream.StreamSupport.stream(page.path("keyPoints").spliterator(), false)
                            .map(JsonNode::asText).filter(value -> !value.isBlank()).reduce((a, b) -> a + "; " + b).orElse("")
                    : "";
            checkpointRepository.insert(new AgentCheckpointEntity(
                    job.getId(), plan.getId(), order++, title,
                    keyPoints.isBlank() ? objective : objective + " Key points: " + keyPoints,
                    agentProperties.defaultCheckpointMaxIterations()
            ));
        }
    }

    private AgentResponse.OutlineResponse outlineResponse(AgentPlanEntity plan) {
        return new AgentResponse.OutlineResponse(
                plan.getJobId(),
                plan.getId(),
                plan.getOutlineRevision(),
                plan.getApprovedOutlineRevision() != null,
                plan.getApprovedOutlineRevision(),
                jsonService.fromJson(plan.getOutlineJson())
        );
    }

    private String sourceExcerpt(Long jobId) {
        StringBuilder builder = new StringBuilder();
        for (AgentSourceEntity source : sourceRepository.findByJobId(jobId)) {
            if (source.getSourceType() == AgentSourceType.FILE && source.getContentText() != null && !source.getContentText().isBlank()) {
                builder.append("- Source file ").append(source.getFileAssetId()).append(":\n");
                builder.append(abbreviate(source.getContentText(), 3000)).append("\n");
            }
        }
        return builder.isEmpty() ? "No text sources provided." : builder.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
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

    private void publishJobEvent(String type, AgentJobEntity job, AgentCheckpointEntity checkpoint, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("presentationId", job.getPresentationId());
        data.put("status", job.getStatus());
        data.put("message", message);
        if (job.getFailureReason() != null) {
            data.put("failureReason", job.getFailureReason());
        }
        if (checkpoint != null) {
            data.put("checkpoint", checkpointPayload(checkpoint));
        }
        progressPublisher.publish(AgentProgressEvent.of(type, job.getId(), job.getPresentationId(), checkpoint == null ? null : checkpoint.getId(), data));
    }

    private void publishMessageEvent(AgentJobEntity job, AgentCheckpointEntity checkpoint, AgentMessageEntity message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("presentationId", job.getPresentationId());
        data.put("checkpointId", checkpoint.getId());
        data.put("messageId", message.getId());
        data.put("roleName", message.getRoleName());
        data.put("contentText", message.getContentText());
        data.put("createdAt", message.getCreatedAt());
        progressPublisher.publish(AgentProgressEvent.of("llm-message", job.getId(), job.getPresentationId(), checkpoint.getId(), data));
    }

    private void publishToolEvent(AgentJobEntity job, AgentCheckpointEntity checkpoint, AgentToolResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("presentationId", job.getPresentationId());
        data.put("checkpointId", checkpoint.getId());
        data.put("toolCallId", result.toolCallId());
        data.put("toolName", result.toolName());
        data.put("response", result.response());
        progressPublisher.publish(AgentProgressEvent.of("tool-call", job.getId(), job.getPresentationId(), checkpoint.getId(), data));
    }

    private void publishArtifactEvent(AgentJobEntity job, AgentCheckpointEntity checkpoint, String artifactType, Long artifactId, String actionName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("presentationId", job.getPresentationId());
        data.put("checkpointId", checkpoint.getId());
        data.put("artifactType", artifactType);
        data.put("artifactId", artifactId);
        data.put("actionName", actionName);
        progressPublisher.publish(AgentProgressEvent.of("artifact", job.getId(), job.getPresentationId(), checkpoint.getId(), data));
    }

    private Map<String, Object> checkpointPayload(AgentCheckpointEntity checkpoint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", checkpoint.getId());
        data.put("order", checkpoint.getCheckpointOrder());
        data.put("title", checkpoint.getTitle());
        data.put("objective", checkpoint.getObjective());
        data.put("status", checkpoint.getStatus());
        data.put("currentIterations", checkpoint.getCurrentIterations());
        data.put("maxIterations", checkpoint.getMaxIterations());
        data.put("failureReason", checkpoint.getFailureReason());
        return data;
    }

}
