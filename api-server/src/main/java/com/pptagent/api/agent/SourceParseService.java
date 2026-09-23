package com.pptagent.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.agent.service.AgentTextExtractionService;
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

import java.util.List;
import java.util.Map;

@Service
public class SourceParseService {
    private final FileAssetRepository files;
    private final AgentJobRepository jobs;
    private final AgentParseJobRepository parseJobs;
    private final AgentSourceBlockRepository blocks;
    private final AgentSourceRepository sources;
    private final AgentTextExtractionService extraction;
    private final ObjectMapper objectMapper;

    public SourceParseService(FileAssetRepository files,
                              AgentJobRepository jobs,
                              AgentParseJobRepository parseJobs,
                              AgentSourceBlockRepository blocks,
                              AgentSourceRepository sources,
                              AgentTextExtractionService extraction,
                              ObjectMapper objectMapper) {
        this.files = files;
        this.jobs = jobs;
        this.parseJobs = parseJobs;
        this.blocks = blocks;
        this.sources = sources;
        this.extraction = extraction;
        this.objectMapper = objectMapper;
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
        AgentJobEntity agentJob = null;
        if (request.agentJobId() != null) {
            agentJob = jobs.findByIdAndOwner(request.agentJobId(), userId)
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent job not found"));
        }

        AgentParseJobEntity parseJob = new AgentParseJobEntity(userId, file.getId(),
                agentJob == null ? null : agentJob.getId(), request.idempotencyKey());
        parseJobs.insert(parseJob);
        try {
            AgentTextExtractionService.ExtractedDocument document = extraction.extractStructured(file);
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
            String reason = exception.getMessage();
            parseJob.fail(reason != null && reason.startsWith("Failed to extract")
                    ? "DOCUMENT_CORRUPTED" : reason);
        } catch (RuntimeException exception) {
            parseJob.fail("DOCUMENT_CORRUPTED");
        }
        parseJobs.updateById(parseJob);
        return response(parseJob);
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
}
