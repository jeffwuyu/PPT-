package com.pptagent.api.auth;

import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.PermissionEntity;
import com.pptagent.domain.auth.ProjectMemberEntity;
import com.pptagent.domain.auth.RoleEntity;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.auth.repository.UserLevelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionPolicyService {

    private final UserLevelRepository userLevelRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserAccessGraphService userAccessGraphService;

    public PermissionPolicyService(
            UserLevelRepository userLevelRepository,
            ProjectMemberRepository projectMemberRepository,
            UserAccessGraphService userAccessGraphService
    ) {
        this.userLevelRepository = userLevelRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userAccessGraphService = userAccessGraphService;
    }

    @Transactional(readOnly = true)
    public boolean hasLevelAtLeast(Long userId, String requiredLevelCode) {
        UserEntity user = loadUser(userId);
        var requiredLevel = userLevelRepository.findByCode(requiredLevelCode)
                .orElseThrow(() -> new PlatformException(ErrorCode.BAD_REQUEST, "Unknown level: " + requiredLevelCode));
        return user.getLevel().getRankOrder() >= requiredLevel.getRankOrder();
    }

    @Transactional(readOnly = true)
    public boolean hasGlobalPermission(Long userId, String permissionCode) {
        return loadUser(userId).getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getCode)
                .anyMatch(permissionCode::equals);
    }

    @Transactional(readOnly = true)
    public boolean canEditProject(Long userId, Long projectId) {
        if (hasGlobalPermission(userId, "PROJECT_ADMIN")) {
            return true;
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMemberEntity::canEdit)
                .orElse(false);
    }

    private UserEntity loadUser(Long userId) {
        return userAccessGraphService.findById(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
