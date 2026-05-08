package com.pptagent.api.collab;

import com.pptagent.api.presentation.PresentationAccessService;
import com.pptagent.collab.security.CollaborationAccessChecker;
import com.pptagent.common.error.PlatformException;
import org.springframework.stereotype.Component;

@Component
/**
 * api-server 对协同权限接口的适配。
 *
 * <p>读取权限沿用 Phase 04 的 Presentation 读取逻辑；编辑权限沿用
 * Phase 02/04 的项目成员策略。这样 WebSocket 协同和 HTTP 编辑接口
 * 使用同一套权限边界。</p>
 */
public class ApiCollaborationAccessChecker implements CollaborationAccessChecker {

    private final PresentationAccessService presentationAccessService;

    public ApiCollaborationAccessChecker(PresentationAccessService presentationAccessService) {
        this.presentationAccessService = presentationAccessService;
    }

    @Override
    public boolean canViewPresentation(Long userId, Long presentationId) {
        try {
            presentationAccessService.loadPresentationForRead(userId, presentationId);
            return true;
        } catch (PlatformException ex) {
            return false;
        }
    }

    @Override
    public boolean canEditPresentation(Long userId, Long presentationId) {
        try {
            presentationAccessService.loadPresentationForEdit(userId, presentationId);
            return true;
        } catch (PlatformException ex) {
            return false;
        }
    }
}
