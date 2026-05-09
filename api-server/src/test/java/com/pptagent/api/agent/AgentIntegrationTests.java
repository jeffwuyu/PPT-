package com.pptagent.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.agent.AgentArtifactType;
import com.pptagent.domain.agent.AgentMemoryScope;
import com.pptagent.domain.agent.AgentToolCallStatus;
import com.pptagent.domain.agent.repository.AgentArtifactRefRepository;
import com.pptagent.domain.agent.repository.AgentMemoryRepository;
import com.pptagent.domain.agent.repository.AgentToolCallRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "ppt.jwt.access-token-ttl=30s")
@AutoConfigureMockMvc
class AgentIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @Autowired
    private AgentToolCallRepository toolCallRepository;

    @Autowired
    private AgentArtifactRefRepository artifactRefRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Test
    void agentPlansApprovesCheckpointsExecutesAndPersistsMemory() throws Exception {
        String token = registerAndToken("agent-flow");
        long presentationId = createPresentation(token, "Agent Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create a concise product strategy PPT").at("/data/id").asLong();

        JsonNode planned = objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.planMarkdown").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(planned.at("/data/planMarkdown").asText()).contains("PPT Production Plan");

        JsonNode approved = objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(approved.at("/data/checkpoints").size()).isGreaterThanOrEqualTo(3);
        assertThat(approved.at("/data/checkpoints/0/maxIterations").asInt()).isEqualTo(3);

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.checkpoints[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.checkpoints[0].currentIterations").value(1));

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides").size()).isEqualTo(4);
        assertThat(toolCallRepository.findByJobId(jobId)).allMatch(call -> call.getStatus() == AgentToolCallStatus.SUCCESS);

        mockMvc.perform(get("/api/agent/jobs/" + jobId + "/memory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void memoryCompressionArchivesOriginalFragments() throws Exception {
        String token = registerAndToken("agent-memory");
        long presentationId = createPresentation(token, "Memory Deck").at("/data/id").asLong();
        String longPrompt = "Create a deck. " + "memory detail ".repeat(40);
        long jobId = createJob(token, presentationId, longPrompt).at("/data/id").asLong();

        var memories = memoryRepository.findByJobId(jobId);
        assertThat(memories).anyMatch(memory -> memory.getMemoryScope() == AgentMemoryScope.SHORT_TERM && memory.isCompressed());
        assertThat(memories).anyMatch(memory -> memory.getMemoryScope() == AgentMemoryScope.SHORT_TERM && memory.isArchived());
    }

    @Test
    void invalidToolRequestIsRejectedAndRecorded() throws Exception {
        String token = registerAndToken("agent-tool");
        long presentationId = createPresentation(token, "Tool Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create a deck with web research").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/tools/call")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"jsoup_web_fetch","arguments":{"url":"not-a-url"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(toolCallRepository.findByJobId(jobId)).singleElement()
                .matches(call -> call.getStatus() == AgentToolCallStatus.REJECTED);
    }

    @Test
    void reactReplanCancelsCheckpointAndRollsBackSlideArtifacts() throws Exception {
        String token = registerAndToken("agent-replan");
        long presentationId = createPresentation(token, "Replan Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "force_replan: create then cancel mismatched work").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.planMarkdown").value(org.hamcrest.Matchers.containsString("Revision: checkpoint")));

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides").size()).isEqualTo(0);
        assertThat(slideRepository.findByPresentationIdOrderByPageOrderAsc(presentationId)).isEmpty();
        assertThat(artifactRefRepository.selectList(null)).anyMatch(ref -> ref.getArtifactType() == AgentArtifactType.SLIDE && !ref.isActive());
    }

    @Test
    void mcpToolRegistryExposesEmbeddedTools() throws Exception {
        String token = registerAndToken("agent-mcp");
        mockMvc.perform(get("/api/agent/mcp/tools")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("jsoup_web_fetch"))
                .andExpect(jsonPath("$.data[1]").value("bailian_image_generate"));
    }

    private JsonNode createJob(String token, long presentationId, String prompt) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"presentationId":%d,"prompt":"%s"}
                                """.formatted(presentationId, prompt.replace("\"", "\\\""))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode createPresentation(String token, String title) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/presentations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode readPreview(String token, long presentationId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/presentations/" + presentationId + "/preview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String registerAndToken(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@example.test","username":"%s%s","password":"StrongPass123"}
                                """.formatted(prefix, suffix, prefix.replace("-", ""), suffix.substring(0, 8))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
