package com.pptagent.api.presentation;

import com.pptagent.api.auth.PermissionPolicyService;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationStatus;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import com.pptagent.domain.auth.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class PresentationAccessService {

    private final PresentationRepository presentationRepository;
    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PermissionPolicyService permissionPolicyService;

    public PresentationAccessService(
            PresentationRepository presentationRepository,
            SlideRepository slideRepository,
            SlideElementRepository slideElementRepository,
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            PermissionPolicyService permissionPolicyService
    ) {
        this.presentationRepository = presentationRepository;
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.permissionPolicyService = permissionPolicyService;
    }

    public UserEntity loadUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "User not found"));
    }

    public PresentationEntity loadPresentationForRead(Long userId, Long presentationId) {
        PresentationEntity presentation = loadActivePresentation(presentationId);
        if (canViewPresentation(userId, presentation)) {
            return presentation;
        }
        throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to view presentation");
    }

    public PresentationEntity loadPresentationForEdit(Long userId, Long presentationId) {
        PresentationEntity presentation = loadActivePresentation(presentationId);
        if (!permissionPolicyService.canEditProject(userId, presentationId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to edit presentation");
        }
        return presentation;
    }

    public PresentationEntity loadActivePresentation(Long presentationId) {
        PresentationEntity presentation = presentationRepository.findNotDeletedById(presentationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Presentation not found"));
        if (presentation.getStatus() == PresentationStatus.DELETED) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Presentation not found");
        }
        userRepository.findWithRolesById(presentation.getOwnerUserId()).ifPresent(presentation::setOwner);
        return presentation;
    }

    public SlideEntity loadSlideForEdit(Long userId, Long slideId) {
        SlideEntity slide = slideRepository.findWithPresentationById(slideId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Slide not found"));
        slide.setPresentation(loadActivePresentation(slide.getPresentationId()));
        if (!permissionPolicyService.canEditProject(userId, slide.getPresentation().getId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to edit presentation");
        }
        return slide;
    }

    public SlideElementEntity loadElementForEdit(Long userId, Long elementId) {
        SlideElementEntity element = slideElementRepository.findWithSlideAndPresentationById(elementId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Element not found"));
        SlideEntity slide = slideRepository.findWithPresentationById(element.getSlideId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Slide not found"));
        slide.setPresentation(loadActivePresentation(slide.getPresentationId()));
        element.setSlide(slide);
        if (!permissionPolicyService.canEditProject(userId, slide.getPresentation().getId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to edit presentation");
        }
        return element;
    }

    private boolean canViewPresentation(Long userId, PresentationEntity presentation) {
        return presentation.getOwnerUserId().equals(userId)
                || permissionPolicyService.hasGlobalPermission(userId, "PROJECT_ADMIN")
                || projectMemberRepository.findByProjectIdAndUserId(presentation.getId(), userId).isPresent();
    }
}
