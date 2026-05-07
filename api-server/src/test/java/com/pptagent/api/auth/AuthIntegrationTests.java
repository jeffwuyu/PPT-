package com.pptagent.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.api.auth.dto.RegisterRequest;
import com.pptagent.domain.auth.ProjectMemberEntity;
import com.pptagent.domain.auth.ProjectRole;
import com.pptagent.domain.auth.UserRoleEntity;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import com.pptagent.domain.auth.repository.RoleRepository;
import com.pptagent.domain.auth.repository.UserLevelRepository;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private PermissionPolicyService permissionPolicyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLevelRepository userLevelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void registerLoginAndReadCurrentUserWithJwt() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "user-" + suffix + "@example.test";
        String password = "StrongPass123";

        JsonNode register = performRegister(email, "user" + suffix.substring(0, 8), password);
        String accessToken = register.at("/data/accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.level").value("FREE"))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.permissions").isArray());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "wrong-" + suffix + "@example.test";
        performRegister(email, "wrong" + suffix.substring(0, 8), "StrongPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"bad-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refreshTokenRotatesAndOldTokenIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "refresh-" + suffix + "@example.test";
        JsonNode register = performRegister(email, "refresh" + suffix.substring(0, 8), "StrongPass123");
        String refreshToken = register.at("/data/refreshToken").asText();

        JsonNode refresh = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(refresh.at("/data/refreshToken").asText()).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "expired-" + suffix + "@example.test";
        JsonNode register = performRegister(email, "expired" + suffix.substring(0, 8), "StrongPass123");
        String accessToken = register.at("/data/accessToken").asText();

        Thread.sleep(1600);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void userLevelRoleAndProjectPermissionPoliciesWork() {
        String suffix = UUID.randomUUID().toString();
        String email = "policy-" + suffix + "@example.test";
        Long projectId = 9001L;

        var registered = authService.register(new RegisterRequest(email, "policy" + suffix.substring(0, 8), "StrongPass123"));
        var user = userRepository.findWithRolesById(registered.user().id()).orElseThrow();

        assertThat(permissionPolicyService.hasLevelAtLeast(user.getId(), "PRO")).isFalse();
        assertThat(permissionPolicyService.hasGlobalPermission(user.getId(), "PROJECT_EDIT")).isFalse();
        assertThat(permissionPolicyService.canEditProject(user.getId(), projectId)).isFalse();

        user.changeLevel(userLevelRepository.findByCode("PRO").orElseThrow());
        userRepository.updateById(user);
        var editorRole = roleRepository.findByCode("ROLE_EDITOR").orElseThrow();
        userRoleRepository.insert(new UserRoleEntity(user.getId(), editorRole.getId()));
        projectMemberRepository.insert(new ProjectMemberEntity(projectId, user, ProjectRole.EDITOR, true));

        assertThat(permissionPolicyService.hasLevelAtLeast(user.getId(), "PRO")).isTrue();
        assertThat(permissionPolicyService.hasGlobalPermission(user.getId(), "PROJECT_EDIT")).isTrue();
        assertThat(permissionPolicyService.canEditProject(user.getId(), projectId)).isTrue();
    }

    private JsonNode performRegister(String email, String username, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","username":"%s","password":"%s"}
                                """.formatted(email, username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }
}
