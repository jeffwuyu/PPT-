package com.pptagent.collab.security;

public interface CollaborationAccessChecker {

    boolean canViewPresentation(Long userId, Long presentationId);

    boolean canEditPresentation(Long userId, Long presentationId);
}
