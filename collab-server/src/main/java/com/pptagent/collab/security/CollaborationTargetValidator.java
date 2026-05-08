package com.pptagent.collab.security;

import com.pptagent.domain.collaboration.CollaborationLockTargetType;

public interface CollaborationTargetValidator {

    void validateTarget(
            Long userId,
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId
    );
}
