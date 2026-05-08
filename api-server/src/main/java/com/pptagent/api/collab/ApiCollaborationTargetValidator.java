package com.pptagent.api.collab;

import com.pptagent.api.presentation.PresentationAccessService;
import com.pptagent.collab.security.CollaborationTargetValidator;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.collaboration.CollaborationLockTargetType;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideEntity;
import org.springframework.stereotype.Component;

@Component
public class ApiCollaborationTargetValidator implements CollaborationTargetValidator {

    private final PresentationAccessService presentationAccessService;

    public ApiCollaborationTargetValidator(PresentationAccessService presentationAccessService) {
        this.presentationAccessService = presentationAccessService;
    }

    @Override
    public void validateTarget(
            Long userId,
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId
    ) {
        switch (targetType) {
            case PRESENTATION -> validatePresentationTarget(userId, presentationId, targetId);
            case SLIDE -> validateSlideTarget(userId, presentationId, targetId);
            case ELEMENT -> validateElementTarget(userId, presentationId, targetId);
        }
    }

    private void validatePresentationTarget(Long userId, Long presentationId, Long targetId) {
        if (!presentationId.equals(targetId)) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Presentation target must match collaboration room");
        }
        presentationAccessService.loadPresentationForEdit(userId, presentationId);
    }

    private void validateSlideTarget(Long userId, Long presentationId, Long targetId) {
        SlideEntity slide = presentationAccessService.loadSlideForEdit(userId, targetId);
        if (!slide.getPresentation().getId().equals(presentationId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Slide target does not belong to collaboration room");
        }
    }

    private void validateElementTarget(Long userId, Long presentationId, Long targetId) {
        SlideElementEntity element = presentationAccessService.loadElementForEdit(userId, targetId);
        if (!element.getSlide().getPresentation().getId().equals(presentationId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Element target does not belong to collaboration room");
        }
    }
}
