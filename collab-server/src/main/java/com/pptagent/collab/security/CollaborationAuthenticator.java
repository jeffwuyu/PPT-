package com.pptagent.collab.security;

import java.util.Optional;

public interface CollaborationAuthenticator {

    Optional<CollaborationUser> authenticate(String token);
}
