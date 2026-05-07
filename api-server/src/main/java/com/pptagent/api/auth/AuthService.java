package com.pptagent.api.auth;

import com.pptagent.api.auth.dto.AuthResponse;
import com.pptagent.api.auth.dto.LoginRequest;
import com.pptagent.api.auth.dto.RefreshTokenRequest;
import com.pptagent.api.auth.dto.RegisterRequest;
import com.pptagent.api.auth.dto.UserProfileResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.auth.AuditLogEntity;
import com.pptagent.domain.auth.PermissionEntity;
import com.pptagent.domain.auth.RefreshTokenEntity;
import com.pptagent.domain.auth.RoleEntity;
import com.pptagent.domain.auth.UserRoleEntity;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.repository.AuditLogRepository;
import com.pptagent.domain.auth.repository.RefreshTokenRepository;
import com.pptagent.domain.auth.repository.RoleRepository;
import com.pptagent.domain.auth.repository.UserLevelRepository;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.auth.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String DEFAULT_LEVEL = "FREE";
    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final UserLevelRepository userLevelRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserAccessGraphService userAccessGraphService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            UserLevelRepository userLevelRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            UserAccessGraphService userAccessGraphService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.userLevelRepository = userLevelRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.userAccessGraphService = userAccessGraphService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Email already registered");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Username already registered");
        }

        var level = userLevelRepository.findByCode(DEFAULT_LEVEL)
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "Default user level missing"));
        RoleEntity role = roleRepository.findByCode(DEFAULT_ROLE)
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "Default user role missing"));
        UserEntity user = new UserEntity(email, request.username(), passwordEncoder.encode(request.password()), level);
        userRepository.insert(user);
        userRoleRepository.insert(new UserRoleEntity(user.getId(), role.getId()));
        UserEntity loadedUser = userAccessGraphService.findById(user.getId())
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "Created user missing"));
        auditLogRepository.insert(new AuditLogEntity(loadedUser, "AUTH_REGISTER", "USER", String.valueOf(loadedUser.getId()), "User registered"));
        return issueTokens(loadedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userAccessGraphService.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new PlatformException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        auditLogRepository.insert(new AuditLogEntity(user, "AUTH_LOGIN", "USER", String.valueOf(user.getId()), "User logged in"));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        if (!refreshToken.isUsable(Instant.now())) {
            throw new PlatformException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }
        refreshToken.revoke();
        refreshTokenRepository.updateById(refreshToken);
        UserEntity user = userAccessGraphService.findById(refreshToken.getUserId())
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "User not found"));
        auditLogRepository.insert(new AuditLogEntity(user, "AUTH_REFRESH", "USER", String.valueOf(user.getId()), "Refresh token rotated"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String email, String refreshToken) {
        UserEntity user = userAccessGraphService.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "User not found"));
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.updateById(token);
            });
        }
        auditLogRepository.insert(new AuditLogEntity(user, "AUTH_LOGOUT", "USER", String.valueOf(user.getId()), "User logged out"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String email) {
        UserEntity user = userAccessGraphService.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "User not found"));
        return toProfile(user);
    }

    private AuthResponse issueTokens(UserEntity user) {
        JwtService.TokenIssue accessToken = jwtService.issueAccessToken(user);
        String rawRefreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                user,
                hash(rawRefreshToken),
                Instant.now().plus(jwtProperties.refreshTokenTtl())
        );
        refreshTokenRepository.insert(refreshToken);
        return new AuthResponse(accessToken.token(), rawRefreshToken, accessToken.expiresAt(), toProfile(user));
    }

    private UserProfileResponse toProfile(UserEntity user) {
        Set<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream().map(PermissionEntity::getCode))
                .collect(Collectors.toUnmodifiableSet());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getLevel().getCode(),
                roles,
                permissions
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SHA-256 is not available");
        }
    }
}
