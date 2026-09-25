package com.pptagent.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.agent.service.AgentTextExtractionService;
import com.pptagent.agent.service.OcrClient;
import com.pptagent.api.agent.dto.SourceParseRequest;
import com.pptagent.api.agent.dto.SourceParseResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentParseJobEntity;
import com.pptagent.domain.agent.AgentSourceBlockEntity;
import com.pptagent.domain.agent.AgentSourceEntity;
import com.pptagent.domain.agent.AgentSourceType;
import com.pptagent.domain.agent.repository.AgentJobRepository;
import com.pptagent.domain.agent.repository.AgentParseJobRepository;
import com.pptagent.domain.agent.repository.AgentSourceBlockRepository;
import com.pptagent.domain.agent.repository.AgentSourceRepository;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.repository.FileAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SourceParseService {
    private final FileAssetRepository files;
    private final AgentJobRepository jobs;
    private final AgentParseJobRepository parseJobs;
    private final AgentSourceBlockRepository blocks;
    private final AgentSourceRepository sources;
    private final AgentTextExtractionService extraction;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final OcrClient ocrClient;

    public SourceParseService(FileAssetRepository files,
                              AgentJobRepository jobs,
                              AgentParseJobRepository parseJobs,
                              AgentSourceBlockRepository blocks,
                              AgentSourceRepository sources,
                              AgentTextExtractionService extraction,
                              ObjectMapper objectMapper,
                              @Qualifier("taskExecutor") TaskExecutor taskExecutor,
                              OcrClient ocrClient) {
        this.files = files;
        this.jobs = jobs;
        this.parseJobs = parseJobs;
        this.blocks = blocks;
        this.sources = sources;
        this.extraction = extraction;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.ocrClient = ocrClient;
    }

    @Transactional
    public SourceParseResponse submit(Long userId, SourceParseRequest request) {
        var existing = parseJobs.findByIdempotency(userId, request.fileAssetId(), request.idempotencyKey());
        if (existing.isPresent()) return response(existing.get());

        FileAssetEntity file = files.findWithOwnerById(request.fileAssetId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Source file not found"));
        if (!userId.equals(file.getOwnerUserId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Source file does not belong to current user");
        }
        if (file.getStatus() != FileAssetStatus.AVAILABLE) {
            throw new PlatformException(ErrorCode.CONFLICT, "Source file is not available");
        }
        if (file.getFileType() != FileType.SOURCE_TEXT) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Only SOURCE_TEXT files can be parsed");
        }
        if (file.getByteSize() > 20L * 1024 * 1024) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "FILE_TOO_LARGE");
        }
        if (!supportedType(file.getOriginalFilename(), file.getContentType())) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "UNSUPPORTED_FORMAT");
        }
        AgentJobEntity agentJob = null;
        if (request.agentJobId() != null) {
            agentJob = jobs.findByIdAndOwner(request.agentJobId(), userId)
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent job not found"));
        }

        AgentParseJobEntity parseJob = new AgentParseJobEntity(userId, file.getId(),
                agentJob == null ? null : agentJob.getId(), request.idempotencyKey());
        parseJobs.insert(parseJob);
        Long parseJobId = parseJob.getId();
        Runnable dispatch = () -> taskExecutor.execute(() -> process(parseJobId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
        } else {
            dispatch.run();
        }
        return response(parseJob);
    }

    @Transactional
    public void process(Long parseJobId) {
        AgentParseJobEntity parseJob = parseJobs.selectById(parseJobId);
        if (parseJob == null || !"QUEUED".equals(parseJob.getStatus())) return;
        String leaseToken = UUID.randomUUID().toString();
        if (parseJobs.claimQueued(parseJobId, leaseToken, Instant.now().plus(Duration.ofMinutes(5))) != 1) return;
        parseJob = parseJobs.selectById(parseJobId);
        FileAssetEntity file = files.findWithOwnerById(parseJob.getFileAssetId()).orElse(null);
        if (file == null) {
            parseJob.fail("SOURCE_FILE_NOT_FOUND");
            parseJobs.updateById(parseJob);
            return;
        }
        AgentJobEntity agentJob = parseJob.getAgentJobId() == null ? null
                : jobs.findByIdAndOwner(parseJob.getAgentJobId(), parseJob.getOwnerUserId()).orElse(null);
        try {
            AgentTextExtractionService.ExtractedDocument document;
            try {
                document = extraction.extractStructured(file);
            } catch (PlatformException exception) {
                if (!"OCR_REQUIRED".equals(exception.getMessage())) throw exception;
                parseJob.markOcrRequired();
                parseJobs.updateById(parseJob);
                parseJob.markOcrRunning();
                parseJobs.updateById(parseJob);
                var ocr = ocrClient.recognize(extraction.loadSourceBytes(file), file.getOriginalFilename());
                List<AgentTextExtractionService.SourceBlock> ocrBlocks = ocr.pages().stream()
                        .filter(page -> page.text() != null && !page.text().isBlank())
                        .map(page -> new AgentTextExtractionService.SourceBlock("page-" + page.page(), "paragraph", page.text(),
                                Map.of("page", page.page(), "ocrProvider", ocr.provider(), "ocrModel", ocr.model(),
                                        "requestId", ocr.requestId(), "confidence", page.confidence())))
                        .toList();
                if (ocrBlocks.isEmpty()) throw new PlatformException(ErrorCode.BAD_REQUEST, "EMPTY_DOCUMENT");
                document = new AgentTextExtractionService.ExtractedDocument("ocr", "ocr-v1", ocrBlocks, List.of());
            }
            for (AgentTextExtractionService.SourceBlock block : document.blocks()) {
                blocks.insert(new AgentSourceBlockEntity(parseJob.getId(), block.blockId(), block.type(), block.text(),
                        writeJson(block.locator())));
            }
            if (agentJob != null) {
                Map<String, Object> metadata = Map.of(
                        "parseJobId", parseJob.getId(),
                        "sourceHash", document.sourceHash(),
                        "parserVersion", document.parserVersion(),
                        "blocks", document.blocks()
                );
                sources.insert(new AgentSourceEntity(agentJob.getId(), AgentSourceType.FILE, file.getId(),
                        document.blocks().stream().map(AgentTextExtractionService.SourceBlock::text)
                                .reduce((a, b) -> a + "\n" + b).orElse(""), writeJson(metadata)));
            }
            parseJob.succeed(document.sourceHash(), document.parserVersion(), document.blocks().size());
        } catch (PlatformException exception) {
            blocks.deleteByParseJobId(parseJob.getId());
            String reason = exception.getMessage();
            parseJob.fail(reason != null && reason.startsWith("Failed to extract")
                    ? "DOCUMENT_CORRUPTED" : reason);
        } catch (RuntimeException exception) {
            blocks.deleteByParseJobId(parseJob.getId());
            parseJob.fail("DOCUMENT_CORRUPTED");
        }
        parseJobs.updateById(parseJob);
    }

    @Transactional(readOnly = true)
    public SourceParseResponse get(Long userId, Long parseJobId) {
        AgentParseJobEntity job = parseJobs.findByOwnerAndId(userId, parseJobId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Parse job not found"));
        return response(job);
    }

    private SourceParseResponse response(AgentParseJobEntity job) {
        List<SourceParseResponse.BlockResponse> result = blocks.findByParseJobId(job.getId()).stream()
                .map(block -> new SourceParseResponse.BlockResponse(block.getBlockId(), block.getBlockType(),
                        block.getText(), block.getLocatorJson()))
                .toList();
        return new SourceParseResponse(job.getId(), job.getFileAssetId(), job.getAgentJobId(), job.getStatus(),
                job.getRevision(), job.getParserVersion(), job.getSourceHash(), job.getTotalBlocks(),
                job.getFailureReason(), result, job.getCreatedAt(), job.getUpdatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize source metadata", exception);
        }
    }

    private boolean supportedType(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".md") || name.endsWith(".txt")
                || type.contains("pdf") || type.contains("wordprocessingml") || type.contains("markdown") || type.startsWith("text/");
    }
}
