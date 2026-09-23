package com.pptagent.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.agent.AgentArtifactType;
import com.pptagent.domain.agent.AgentMemoryScope;
import com.pptagent.domain.agent.AgentToolCallStatus;
import com.pptagent.agent.progress.AgentProgressEvent;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private AgentProgressStreamService progressStreamService;

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
        assertThat(preview.at("/data/slides/0/elements").size()).isEqualTo(3);
        assertThat(preview.at("/data/slides/0/elements/0/elementKey").asText()).isEqualTo("agent-%d-cp-1-title".formatted(jobId));
        assertThat(preview.at("/data/slides/0/elements/0/type").asText()).isEqualTo("TEXT");
        assertThat(preview.at("/data/slides/0/elements/0/content/text").asText()).contains("Analyze source material");
        assertThat(preview.at("/data/slides/0/elements/1/elementKey").asText()).isEqualTo("agent-%d-cp-1-body".formatted(jobId));
        assertThat(preview.at("/data/slides/0/elements/2/elementKey").asText()).isEqualTo("agent-%d-cp-1-image".formatted(jobId));
        assertThat(preview.at("/data/slides/0/elements/2/type").asText()).isEqualTo("IMAGE");
        assertThat(preview.at("/data/slides/0/elements/2/content/placeholder").asBoolean()).isTrue();
        assertThat(preview.at("/data/slides/0/elements/2/content/fallbackReason").asText()).contains("No available");
        assertThat(preview.at("/data/slides/0/elements/2/x").asDouble()).isEqualTo(768.0d);
        assertThat(preview.at("/data/slides/0/elements/2/width").asDouble()).isEqualTo(410.0d);
        assertThat(preview.at("/data/slides/0/elements/0/style/themeAccent").asText()).isEqualTo("#2563eb");
        assertThat(toolCallRepository.findByJobId(jobId)).allMatch(call -> call.getStatus() == AgentToolCallStatus.SUCCESS);

        mockMvc.perform(get("/api/agent/jobs/" + jobId + "/memory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void agentExecutionUsesAvailableImageSourcesInSlideJson() throws Exception {
        String token = registerAndToken("agent-image-source");
        long presentationId = createPresentation(token, "Image Source Deck").at("/data/id").asLong();
        long imageFileId = createAvailableImageFile(token);
        long jobId = createJob(token, presentationId, "Create a deck using the supplied image", imageFileId).at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"));

        JsonNode preview = readPreview(token, presentationId);
        JsonNode imageElement = preview.at("/data/slides/0/elements/2");
        assertThat(imageElement.at("/content/placeholder").asBoolean()).isFalse();
        assertThat(imageElement.at("/content/fileAssetId").asLong()).isEqualTo(imageFileId);
        assertThat(imageElement.at("/content/publicUrl").asText()).contains("https://cos.example.test/");
    }

    @Test
    void slideValidationRetriesBeforeCompletingCheckpoint() throws Exception {
        String token = registerAndToken("agent-retry");
        long presentationId = createPresentation(token, "Retry Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "retry_slide_validation_once: create a deck").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.checkpoints[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.checkpoints[0].currentIterations").value(2));

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides").size()).isEqualTo(4);
    }

    @Test
    void slideValidationFailureStopsAtMaxIterationsAndFailsJob() throws Exception {
        String token = registerAndToken("agent-validation-fail");
        long presentationId = createPresentation(token, "Validation Fail Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "force_slide_validation_failure: create a deck").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("Forced slide validation failure"))
                .andExpect(jsonPath("$.data.checkpoints[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.checkpoints[0].currentIterations").value(3));

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides").size()).isEqualTo(0);
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

    @Test
    void outlineUsesRevisionAndRejectsStaleEditors() throws Exception {
        String token = registerAndToken("agent-outline");
        long presentationId = createPresentation(token, "Outline Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create an outline").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode outline = objectMapper.readTree(mockMvc.perform(get("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1))
                .andReturn().getResponse().getContentAsString());
        String update = "{\"expectedRevision\":1,\"outline\":{\"pages\":[{\"pageId\":\"page-1\",\"order\":1,\"title\":\"Executive summary\",\"objective\":\"Explain the product\",\"keyPoints\":[\"Problem\",\"Solution\"],\"sourceBlockIds\":[]}]}}";
        mockMvc.perform(put("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));

        mockMvc.perform(put("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(put("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":2,\"outline\":{\"pages\":[{\"pageId\":\"page-1\",\"order\":1,\"title\":\"Executive summary\",\"objective\":\"Explain the product\",\"keyPoints\":[\"Problem\",\"Solution\"],\"sourceBlockIds\":[]}]},\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approved").value(true))
                .andExpect(jsonPath("$.data.approvedRevision").value(3));
    }

    @Test
    void generationSubmissionReturnsAcceptedAndReusesIdempotencyKey() throws Exception {
        String token = registerAndToken("agent-generation-async");
        long presentationId = createPresentation(token, "Async Generation Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create an async deck").at("/data/id").asLong();
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedRevision":1,"approve":true,"outline":{"pages":[
                                  {"pageId":"p-1","order":1,"title":"Summary","objective":"Summarize","keyPoints":["A"],"sourceBlockIds":[]},
                                  {"pageId":"p-2","order":2,"title":"Plan","objective":"Explain plan","keyPoints":["B"],"sourceBlockIds":[]}
                                ]}}
                                """))
                .andExpect(status().isOk());

        JsonNode submitted = objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs/" + jobId + "/generations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"generation-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.pages.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        long generationId = submitted.at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/generations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"generation-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(generationId));

        mockMvc.perform(get("/api/agent/generations/" + generationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentJobId").value(jobId))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void progressEventsReplayFromPersistentCursor() throws Exception {
        String token = registerAndToken("agent-event-replay");
        long presentationId = createPresentation(token, "Event Replay Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create a deck").at("/data/id").asLong();
        progressStreamService.publish(AgentProgressEvent.of("checkpoint", jobId, presentationId, null,
                java.util.Map.of("message", "persisted")));

        var replayed = progressStreamService.replayAfter(jobId, "0");
        assertThat(replayed).hasSize(1);
        assertThat(replayed.get(0).eventId()).isEqualTo(replayed.get(0).sequence() + "");
        assertThat(replayed.get(0).data()).containsEntry("message", "persisted");
    }

    @Test
    void generatedPageUsesApprovedKeyPointsAsItsBody() throws Exception {
        String token = registerAndToken("agent-approved-content");
        long presentationId = createPresentation(token, "Approved Content Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create a deck from the approved outline").at("/data/id").asLong();
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/agent/jobs/" + jobId + "/outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedRevision":1,"approve":true,"outline":{"pages":[
                                  {"pageId":"content-page-1","order":1,"title":"Verified content",
                                   "objective":"Explain verified controls","keyPoints":["Use revision checks","Reject stale writes"],"sourceBlockIds":[]}
                                ]}}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"));

        JsonNode preview = readPreview(token, presentationId);
        String body = preview.at("/data/slides/0/elements/1/content/text").asText();
        assertThat(body).contains("Use revision checks", "Reject stale writes");
        assertThat(body).doesNotContain("Support it with one visual");
    }

    private JsonNode createJob(String token, long presentationId, String prompt) throws Exception {
        return createJob(token, presentationId, prompt, null);
    }

    private JsonNode createJob(String token, long presentationId, String prompt, Long imageAssetId) throws Exception {
        String imageAssetIds = imageAssetId == null ? "" : ",\"imageAssetIds\":[%d]".formatted(imageAssetId);
        return objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"presentationId":%d,"prompt":"%s"%s}
                                """.formatted(presentationId, prompt.replace("\"", "\\\""), imageAssetIds)))
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

    private long createAvailableImageFile(String token) throws Exception {
        JsonNode tokenResponse = objectMapper.readTree(mockMvc.perform(post("/api/files/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileType":"USER_IMAGE",
                                  "originalFilename":"agent-figure.png",
                                  "contentType":"image/png",
                                  "byteSize":128
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        long fileId = tokenResponse.at("/data/fileId").asLong();
        mockMvc.perform(post("/api/files/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileId":%d}
                                """.formatted(fileId)))
                .andExpect(status().isOk());
        return fileId;
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
