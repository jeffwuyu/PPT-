package com.pptagent.api.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.image.ImageSuitabilityStatus;
import com.pptagent.domain.image.repository.ImageAssetRepository;
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
class ImageToolsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImageAssetRepository imageAssetRepository;

    @Test
    void topicSearchReturnsCandidateImagesWithoutRag() throws Exception {
        String token = registerAndToken("image-search");

        mockMvc.perform(post("/api/images/search-candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"enterprise data strategy","limit":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceUrl").isNotEmpty())
                .andExpect(jsonPath("$.data[0].licenseNote").isNotEmpty())
                .andExpect(jsonPath("$.data[0].width").value(1360))
                .andExpect(jsonPath("$.data[0].height").value(760))
                .andExpect(jsonPath("$.data[0].imageFormat").value("jpg"));
    }

    @Test
    void mockVlmEvaluatesAndImageAssetPersists() throws Exception {
        String token = registerAndToken("image-vlm");
        JsonNode candidateResponse = objectMapper.readTree(mockMvc.perform(post("/api/images/search-candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"data platform roadmap","limit":1}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode candidate = candidateResponse.at("/data/0");

        JsonNode evaluatedResponse = objectMapper.readTree(mockMvc.perform(post("/api/images/evaluate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectMapper.createObjectNode().set("candidate", candidate))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.description").isNotEmpty())
                .andExpect(jsonPath("$.data.scene").value("data visualization"))
                .andExpect(jsonPath("$.data.qualityScore").value(0.9))
                .andExpect(jsonPath("$.data.suitable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode evaluated = evaluatedResponse.get("data");
        JsonNode savedResponse = objectMapper.readTree(mockMvc.perform(post("/api/images/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectMapper.createObjectNode().set("image", evaluated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.suitabilityStatus").value("SUITABLE"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        long imageId = savedResponse.at("/data/id").asLong();
        assertThat(imageAssetRepository.selectById(imageId))
                .satisfies(asset -> {
                    assertThat(asset.getTagsJson()).contains("data", "ppt-ready");
                    assertThat(asset.getSuitabilityStatus()).isEqualTo(ImageSuitabilityStatus.SUITABLE);
                    assertThat(asset.isSuitableForPpt()).isTrue();
                });

        mockMvc.perform(get("/api/images/assets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(imageId));
    }

    @Test
    void webPageCandidateSearchUsesMockPageWithoutExternalNetwork() throws Exception {
        String token = registerAndToken("image-web");

        mockMvc.perform(post("/api/images/search-candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"market launch","sourceUrl":"https://mock.local/gallery","limit":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceType").value("WEB_PAGE"))
                .andExpect(jsonPath("$.data[0].publicUrl").value("https://mock.local/gallery/hero.jpg"));
    }

    @Test
    void mcpToolRegistryExposesPhaseSevenImageTools() throws Exception {
        String token = registerAndToken("image-mcp");

        mockMvc.perform(get("/api/agent/mcp/tools")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.hasItems(
                        "image_candidate_search",
                        "image_vlm_evaluate",
                        "image_asset_save"
                )));
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
