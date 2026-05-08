package com.pptagent.api.collab;

import com.pptagent.api.auth.JwtService;
import com.pptagent.api.security.AuthenticatedUser;
import com.pptagent.collab.security.CollaborationAuthenticator;
import com.pptagent.collab.security.CollaborationUser;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
/**
 * api-server 对协同鉴权接口的适配。
 *
 * <p>collab-server 不直接依赖 api-server，所以这里把现有 JWT 能力
 * 翻译成协同模块需要的轻量 `CollaborationUser`。token 无效、过期或
 * 找不到用户时返回 empty，由握手拦截器映射为 401。</p>
 */
public class ApiCollaborationAuthenticator implements CollaborationAuthenticator {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public ApiCollaborationAuthenticator(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Optional<CollaborationUser> authenticate(String token) {
        try {
            String email = jwtService.extractSubject(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, userDetails.getUsername()) || !(userDetails instanceof AuthenticatedUser user)) {
                return Optional.empty();
            }
            return Optional.of(new CollaborationUser(
                    user.id(),
                    user.email(),
                    user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
