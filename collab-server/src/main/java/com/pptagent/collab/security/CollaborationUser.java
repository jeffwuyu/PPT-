package com.pptagent.collab.security;

import java.util.Collection;

public record CollaborationUser(
        Long id,
        String email,
        Collection<String> authorities
) {
}
