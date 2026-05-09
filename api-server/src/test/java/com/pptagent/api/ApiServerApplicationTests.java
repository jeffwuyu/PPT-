package com.pptagent.api;

import com.pptagent.agent.config.AgentProperties;
import com.pptagent.collab.config.CollaborationProperties;
import com.pptagent.infrastructure.config.CosStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ApiServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CosStorageProperties cosStorageProperties;

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private CollaborationProperties collaborationProperties;

    @Test
    void contextLoadsWithFoundationProperties() {
        assertThat(cosStorageProperties.mockEnabled()).isTrue();
        assertThat(cosStorageProperties.bucket()).isEqualTo("test-bucket");
        assertThat(agentProperties.mockLlmEnabled()).isTrue();
        assertThat(agentProperties.maxPlanSlides()).isEqualTo(12);
        assertThat(agentProperties.effectiveDefaultModel()).isEqualTo("mock-llm");
        assertThat(agentProperties.effectiveImageModel()).isEqualTo("qwen-image-2.0-pro");
        assertThat(agentProperties.effectiveQwenBaseUrl()).isEqualTo("https://dashscope.example.test/compatible-mode/v1");
        assertThat(collaborationProperties.websocketEndpoint()).isEqualTo("/ws/presentations");
    }

    @Test
    void healthEndpointIsPublicAndReportsFoundationModules() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.activeProfiles[0]").value("test"))
                .andExpect(jsonPath("$.data.modules.length()").value(7));
    }
}
