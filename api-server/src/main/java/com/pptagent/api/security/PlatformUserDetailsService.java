package com.pptagent.api.security;

import com.pptagent.api.auth.UserAccessGraphService;
import com.pptagent.domain.auth.PermissionEntity;
import com.pptagent.domain.auth.RoleEntity;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.UserStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final UserAccessGraphService userAccessGraphService;

    public PlatformUserDetailsService(UserAccessGraphService userAccessGraphService) {
        this.userAccessGraphService = userAccessGraphService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userAccessGraphService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserDetails(user);
    }

    private AuthenticatedUser toUserDetails(UserEntity user) {
        var authorities = user.getRoles().stream()
                .flatMap(this::authoritiesFor)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                authorities
        );
    }

    private Stream<String> authoritiesFor(RoleEntity role) {
        return Stream.concat(
                Stream.of(role.getCode()),
                role.getPermissions().stream().map(PermissionEntity::getCode)
        );
    }
}
