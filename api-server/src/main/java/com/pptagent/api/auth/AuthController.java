package com.pptagent.api.auth;

import com.pptagent.api.auth.dto.AuthResponse;
import com.pptagent.api.auth.dto.LoginRequest;
import com.pptagent.api.auth.dto.LogoutRequest;
import com.pptagent.api.auth.dto.RefreshTokenRequest;
import com.pptagent.api.auth.dto.RegisterRequest;
import com.pptagent.api.auth.dto.UserProfileResponse;
import com.pptagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication, @RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(authentication.getName(), refreshToken);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.profile(authentication.getName()));
    }
}
