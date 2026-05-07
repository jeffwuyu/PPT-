package com.pptagent.api.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.repository.FileAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "ppt.cos.mock-fail-upload=true",
        "ppt.jwt.access-token-ttl=30s"
})
@AutoConfigureMockMvc
class FileUploadFailureIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileAssetRepository fileAssetRepository;

    @Test
    void failedCosUploadMarksFileAsFailed() throws Exception {
        String accessToken = registerAndToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "should-fail.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "USER_IMAGE")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));

        assertThat(fileAssetRepository.findAll())
                .filteredOn(asset -> asset.getOriginalFilename().equals("should-fail.png"))
                .singleElement()
                .extracting("status")
                .isEqualTo(FileAssetStatus.FAILED);
    }

    private String registerAndToken() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"failure-%s@example.test","username":"failure%s","password":"StrongPass123"}
                                """.formatted(suffix, suffix.substring(0, 8))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
