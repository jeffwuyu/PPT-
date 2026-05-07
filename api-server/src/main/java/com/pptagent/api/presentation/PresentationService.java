package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.ProjectMemberEntity;
import com.pptagent.domain.auth.ProjectRole;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationVersionEntity;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.domain.presentation.repository.PresentationVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PresentationService {

    private static final int DEFAULT_PAGE_WIDTH = 1280;
    private static final int DEFAULT_PAGE_HEIGHT = 720;

    private final PresentationRepository presentationRepository;
    private final PresentationVersionRepository presentationVersionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PresentationAccessService accessService;
    private final PresentationJsonService jsonService;
    private final PresentationResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public PresentationService(
            PresentationRepository presentationRepository,
            PresentationVersionRepository presentationVersionRepository,
            ProjectMemberRepository projectMemberRepository,
            PresentationAccessService accessService,
            PresentationJsonService jsonService,
            PresentationResponseMapper responseMapper,
            ObjectMapper objectMapper
    ) {
        this.presentationRepository = presentationRepository;
        this.presentationVersionRepository = presentationVersionRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.accessService = accessService;
        this.jsonService = jsonService;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PresentationResponse createPresentation(Long userId, PresentationRequests.CreatePresentationRequest request) {
        UserEntity owner = accessService.loadUser(userId);
        PresentationEntity presentation = new PresentationEntity(
                owner,
                requireTitle(request.title()),
                jsonService.positiveOrDefault(request.pageWidth(), DEFAULT_PAGE_WIDTH, "pageWidth"),
                jsonService.positiveOrDefault(request.pageHeight(), DEFAULT_PAGE_HEIGHT, "pageHeight"),
                jsonService.toJson(jsonService.defaultObject(request.theme()))
        );
        presentationRepository.insert(presentation);
        projectMemberRepository.insert(new ProjectMemberEntity(presentation.getId(), owner, ProjectRole.OWNER, true));
        return responseMapper.buildPresentationResponse(presentation, false);
    }

    @Transactional(readOnly = true)
    public PresentationResponse getPresentation(Long userId, Long presentationId) {
        PresentationEntity presentation = accessService.loadPresentationForRead(userId, presentationId);
        return responseMapper.buildPresentationResponse(presentation, false);
    }

    @Transactional
    public PresentationResponse updatePresentation(Long userId, Long presentationId, PresentationRequests.UpdatePresentationRequest request) {
        PresentationEntity presentation = accessService.loadPresentationForEdit(userId, presentationId);
        presentation.update(
                requireTitle(request.title()),
                jsonService.positive(request.pageWidth(), "pageWidth"),
                jsonService.positive(request.pageHeight(), "pageHeight"),
                jsonService.toJson(jsonService.defaultObject(request.theme()))
        );
        presentationRepository.updateById(presentation);
        return responseMapper.buildPresentationResponse(presentation, false);
    }

    @Transactional
    public void deletePresentation(Long userId, Long presentationId) {
        PresentationEntity presentation = accessService.loadPresentationForEdit(userId, presentationId);
        presentation.markDeleted();
        presentationRepository.updateById(presentation);
    }

    @Transactional(readOnly = true)
    public PresentationResponse preview(Long userId, Long presentationId) {
        PresentationEntity presentation = accessService.loadPresentationForRead(userId, presentationId);
        return responseMapper.buildPresentationResponse(presentation, true);
    }

    @Transactional
    public PresentationResponse.VersionResponse createVersion(Long userId, Long presentationId, PresentationRequests.CreateVersionRequest request) {
        UserEntity user = accessService.loadUser(userId);
        PresentationEntity presentation = accessService.loadPresentationForEdit(userId, presentationId);
        int versionNumber = presentation.nextVersionNumber();
        presentationRepository.updateById(presentation);

        String snapshotJson = jsonService.toJson(objectMapper.valueToTree(responseMapper.buildPresentationResponse(presentation, true)));
        PresentationVersionEntity version = new PresentationVersionEntity(
                presentation,
                versionNumber,
                request.reason(),
                snapshotJson,
                user
        );
        presentationVersionRepository.insert(version);
        return responseMapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<PresentationResponse.VersionSummaryResponse> listVersions(Long userId, Long presentationId) {
        accessService.loadPresentationForRead(userId, presentationId);
        return presentationVersionRepository.findByPresentationIdOrderByVersionNumberDesc(presentationId).stream()
                .map(version -> new PresentationResponse.VersionSummaryResponse(
                        version.getVersionNumber(),
                        version.getReason(),
                        version.getCreatedByUserId(),
                        version.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PresentationResponse.VersionResponse getVersion(Long userId, Long presentationId, int versionNumber) {
        accessService.loadPresentationForRead(userId, presentationId);
        PresentationVersionEntity version = presentationVersionRepository.findByPresentationIdAndVersionNumber(presentationId, versionNumber)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Presentation version not found"));
        return responseMapper.toVersionResponse(version);
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 120) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Presentation title is required and must be at most 120 characters");
        }
        return title;
    }
}
