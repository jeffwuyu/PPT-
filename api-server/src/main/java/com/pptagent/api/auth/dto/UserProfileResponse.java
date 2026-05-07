package com.pptagent.api.auth.dto;

import java.util.Set;

public record UserProfileResponse(
        Long id,
        String email,
        String username,
        String level,
        Set<String> roles,
        Set<String> permissions
) {
}
