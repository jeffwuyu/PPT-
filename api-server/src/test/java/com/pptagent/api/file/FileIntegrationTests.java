package com.pptagent.api.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "ppt.jwt.access-token-ttl=30s")
@AutoConfigureMockMvc
class FileIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void presignedUploadCanBeCreatedCompletedAndRead() throws Exception {
        String accessToken = registerAndToken("file-token");

        JsonNode tokenResponse = objectMapper.readTree(mockMvc.perform(post("/api/files/upload-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileType":"USER_IMAGE",
                                  "originalFilename":"chart.png",
                                  "contentType":"image/png",
                                  "byteSize":128
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("mockUploadToken=true")))
                .andReturn()
                .getResponse()
                .getContentAsString());

        long fileId = tokenResponse.at("/data/fileId").asLong();
        assertThat(tokenResponse.at("/data/objectKey").asText()).contains("users/");

        mockMvc.perform(post("/api/files/complete")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileId":%d}
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.publicUrl").value(org.hamcrest.Matchers.containsString("https://cos.example.test/")));

        mockMvc.perform(get("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("chart.png"))
                .andExpect(jsonPath("$.data.uploadMode").value("PRESIGNED"));
    }

    @Test
    void serverUploadStoresMetadataAndMockCosObject() throws Exception {
        String accessToken = registerAndToken("file-upload");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "COVER_IMAGE")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.uploadMode").value("SERVER"))
                .andExpect(jsonPath("$.data.publicUrl").value(org.hamcrest.Matchers.containsString("cover.png")));
    }

    @Test
    void invalidTypeAndOversizeFilesAreRejectedBeforeCosUpload() throws Exception {
        String accessToken = registerAndToken("file-invalid");

        mockMvc.perform(post("/api/files/upload-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileType":"USER_IMAGE",
                                  "originalFilename":"notes.txt",
                                  "contentType":"text/plain",
                                  "byteSize":128
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/files/upload-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileType":"SOURCE_TEXT",
                                  "originalFilename":"big.txt",
                                  "contentType":"text/plain",
                                  "byteSize":1048577
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File exceeds max upload size"));
    }

    @Test
    void fileMetadataIsOwnerScoped() throws Exception {
        String ownerToken = registerAndToken("file-owner");
        String otherToken = registerAndToken("file-other");

        JsonNode tokenResponse = objectMapper.readTree(mockMvc.perform(post("/api/files/upload-token")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileType":"USER_IMAGE",
                                  "originalFilename":"private.png",
                                  "contentType":"image/png",
                                  "byteSize":16
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(get("/api/files/" + tokenResponse.at("/data/fileId").asLong())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void fileEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/api/files/1"))
                .andExpect(status().isUnauthorized());
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
