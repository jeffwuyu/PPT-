package com.pptagent.api.auth;

import com.pptagent.domain.auth.PermissionEntity;
import com.pptagent.domain.auth.RoleEntity;
import com.pptagent.domain.auth.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenIssue issueAccessToken(UserEntity user) {
        Instant expiresAt = Instant.now().plus(properties.accessTokenTtl());
        List<String> authorities = user.getRoles().stream()
                .flatMap(role -> Stream.concat(
                        Stream.of(role.getCode()),
                        role.getPermissions().stream().map(PermissionEntity::getCode)
                ))
                .distinct()
                .sorted()
                .toList();
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("level", user.getLevel().getCode())
                .claim("authorities", authorities)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new TokenIssue(token, expiresAt);
    }

    public String extractSubject(String token) {
        return claims(token).getSubject();
    }

    public boolean isTokenValid(String token, String expectedSubject) {
        return expectedSubject.equals(extractSubject(token));
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record TokenIssue(String token, Instant expiresAt) {
    }
}
