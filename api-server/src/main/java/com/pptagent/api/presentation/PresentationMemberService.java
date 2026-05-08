package com.pptagent.api.presentation;

import com.pptagent.api.auth.PermissionPolicyService;
import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.collab.websocket.CollaborationSessionRegistry;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.ProjectMemberEntity;
import com.pptagent.domain.auth.ProjectRole;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresentationMemberService {

    private final PresentationAccessService accessService;
    private final PermissionPolicyService permissionPolicyService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final CollaborationSessionRegistry sessionRegistry;

    public PresentationMemberService(
            PresentationAccessService accessService,
            PermissionPolicyService permissionPolicyService,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            CollaborationSessionRegistry sessionRegistry
    ) {
        this.accessService = accessService;
        this.permissionPolicyService = permissionPolicyService;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional
    public void sharePresentation(
            Long actorUserId,
            Long presentationId,
            PresentationRequests.SharePresentationMemberRequest request
    ) {
        PresentationEntity presentation = loadPresentationForMemberAdmin(actorUserId, presentationId);
        UserEntity target = loadTargetUser(request.email());
        rejectOwnerMutation(presentation, target);

        boolean canEdit = Boolean.TRUE.equals(request.canEdit());
        ProjectRole role = canEdit ? ProjectRole.EDITOR : ProjectRole.VIEWER;
        projectMemberRepository.findAnyByProjectIdAndUserId(presentationId, target.getId())
                .ifPresentOrElse(member -> {
                    member.restore(role, canEdit);
                    projectMemberRepository.updateById(member);
                }, () -> projectMemberRepository.insert(new ProjectMemberEntity(presentationId, target, role, canEdit)));
    }

    @Transactional
    public void removePresentationMember(Long actorUserId, Long presentationId, String email) {
        PresentationEntity presentation = loadPresentationForMemberAdmin(actorUserId, presentationId);
        UserEntity target = loadTargetUser(email);
        rejectOwnerMutation(presentation, target);

        projectMemberRepository.deleteByProjectIdAndUserId(presentationId, target.getId());
        sessionRegistry.closeUserSessions(presentationId, target.getId());
    }

    private PresentationEntity loadPresentationForMemberAdmin(Long actorUserId, Long presentationId) {
        PresentationEntity presentation = accessService.loadActivePresentation(presentationId);
        if (presentation.getOwnerUserId().equals(actorUserId)
                || permissionPolicyService.hasGlobalPermission(actorUserId, "PROJECT_ADMIN")) {
            return presentation;
        }
        throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to manage presentation members");
    }

    private UserEntity loadTargetUser(String email) {
        String normalized = normalizeEmail(email);
        return userRepository.findByEmail(normalized)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Target user not found"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Target email is required");
        }
        return email.trim().toLowerCase();
    }

    private void rejectOwnerMutation(PresentationEntity presentation, UserEntity target) {
        if (presentation.getOwnerUserId().equals(target.getId())) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Presentation owner membership cannot be changed");
        }
    }
}
