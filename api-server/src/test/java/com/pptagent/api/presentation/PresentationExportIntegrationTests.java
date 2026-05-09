package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.agent.repository.AgentJobRepository;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.PresentationExportStatus;
import com.pptagent.domain.presentation.PresentationStatus;
import com.pptagent.domain.presentation.repository.PresentationExportRepository;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.infrastructure.config.CosStorageProperties;
import com.pptagent.infrastructure.storage.ObjectStorageClient;
import com.pptagent.infrastructure.storage.ObjectStorageException;
import com.pptagent.infrastructure.storage.PresignedUpload;
import com.pptagent.infrastructure.storage.StoredObject;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "ppt.jwt.access-token-ttl=30s")
@AutoConfigureMockMvc
class PresentationExportIntegrationTests {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    );

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private PresentationRepository presentationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PresentationExportRepository presentationExportRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private FileAssetRepository fileAssetRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private AgentJobRepository agentJobRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private SwitchableObjectStorageClient objectStorageClient;

    @AfterEach
    void resetStorage() {
        objectStorageClient.failUploads(false);
    }

    @Test
    void fixedSlideJsonExportsPptxThumbnailsVersionAndPublishedState() throws Exception {
        String token = registerAndToken("export-fixed");
        long presentationId = createPresentation(token, "Export Deck").at("/data/id").asLong();
        long slideId = createSlide(token, presentationId, "Launch Plan").at("/data/slides/0/id").asLong();
        long imageFileId = uploadServerImage(token);
        createElement(token, slideId, """
                {
                  "type":"TEXT",
                  "x":48,
                  "y":48,
                  "width":520,
                  "height":96,
                  "zIndex":3,
                  "content":{"text":"Export Title"},
                  "style":{"fontSize":36,"fontWeight":"700","color":"#111827"}
                }
                """);
        createElement(token, slideId, """
                {
                  "type":"IMAGE",
                  "x":600,
                  "y":120,
                  "width":360,
                  "height":220,
                  "zIndex":1,
                  "content":{"fileAssetId":%d,"altText":"Product chart"},
                  "style":{"fit":"cover"}
                }
                """.formatted(imageFileId));

        JsonNode exported = objectMapper.readTree(mockMvc.perform(post("/api/presentations/" + presentationId + "/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.thumbnailFileIds.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString());

        long exportId = exported.at("/data/exportId").asLong();
        long pptxFileId = exported.at("/data/pptxFileId").asLong();
        long coverFileId = exported.at("/data/coverFileId").asLong();
        long thumbnailFileId = exported.at("/data/thumbnailFileIds/0").asLong();
        assertThat(coverFileId).isEqualTo(thumbnailFileId);
        assertThat(exported.at("/data/pptxUrl").asText()).contains("https://cos.example.test/");
        assertThat(exported.at("/data/coverUrl").asText()).contains("https://cos.example.test/");

        assertThat(presentationRepository.selectById(presentationId).getStatus()).isEqualTo(PresentationStatus.PUBLISHED);
        assertThat(presentationRepository.selectById(presentationId).getCurrentVersionNumber()).isEqualTo(1);
        assertThat(presentationExportRepository.selectById(exportId).getStatus()).isEqualTo(PresentationExportStatus.AVAILABLE);
        assertThat(presentationExportRepository.selectById(exportId).getVersionNumber()).isEqualTo(1);
        assertThat(fileAssetRepository.selectById(pptxFileId).getStatus()).isEqualTo(FileAssetStatus.AVAILABLE);
        assertThat(fileAssetRepository.selectById(coverFileId).getStatus()).isEqualTo(FileAssetStatus.AVAILABLE);

        byte[] pptxBytes = objectStorageClient.getObjectBytes(fileAssetRepository.selectById(pptxFileId).getObjectKey());
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            assertThat(show.getSlides()).hasSize(1);
            String text = show.getSlides().get(0).getShapes().stream()
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .map(XSLFTextShape::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("Export Title");
        }

        byte[] thumbnailBytes = objectStorageClient.getObjectBytes(fileAssetRepository.selectById(thumbnailFileId).getObjectKey());
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));
        assertThat(thumbnail.getWidth()).isEqualTo(1280);
        assertThat(thumbnail.getHeight()).isEqualTo(720);
    }

    @Test
    void generatedAgentJobPublishesPresentationAndMarksJobPublished() throws Exception {
        String token = registerAndToken("export-agent");
        long presentationId = createPresentation(token, "Agent Export Deck").at("/data/id").asLong();
        long jobId = createJob(token, presentationId, "Create a concise publishable strategy PPT").at("/data/id").asLong();

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/approve-plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/execute").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"));

        mockMvc.perform(post("/api/agent/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.thumbnailFileIds.length()").value(4));

        assertThat(agentJobRepository.selectById(jobId).getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(presentationRepository.selectById(presentationId).getStatus()).isEqualTo(PresentationStatus.PUBLISHED);
    }

    @Test
    void mockStorageUploadFailureLeavesFailedExportRecord() throws Exception {
        String token = registerAndToken("export-failure");
        long presentationId = createPresentation(token, "Failed Export Deck").at("/data/id").asLong();
        long slideId = createSlide(token, presentationId, "Failure").at("/data/slides/0/id").asLong();
        createElement(token, slideId, """
                {
                  "type":"TEXT",
                  "x":48,
                  "y":48,
                  "width":520,
                  "height":96,
                  "content":{"text":"Should fail upload"},
                  "style":{"fontSize":30}
                }
                """);

        objectStorageClient.failUploads(true);
        mockMvc.perform(post("/api/presentations/" + presentationId + "/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));

        assertThat(presentationExportRepository.findByPresentationIdOrderByCreatedAtDesc(presentationId))
                .first()
                .satisfies(export -> {
                    assertThat(export.getStatus()).isEqualTo(PresentationExportStatus.FAILED);
                    assertThat(export.getFailureReason()).contains("Export artifact upload failed");
                });
        assertThat(presentationRepository.selectById(presentationId).getStatus()).isEqualTo(PresentationStatus.ACTIVE);
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

    private JsonNode createSlide(String token, long presentationId, String title) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/presentations/" + presentationId + "/slides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","background":{"color":"#ffffff"},"speakerNotes":"speaker notes"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode createElement(String token, long slideId, String content) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/slides/" + slideId + "/elements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private long uploadServerImage(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "figure.png", "image/png", PNG_BYTES);
        JsonNode response = objectMapper.readTree(mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "USER_IMAGE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return response.at("/data/id").asLong();
    }

    private JsonNode createJob(String token, long presentationId, String prompt) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/agent/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"presentationId":%d,"prompt":"%s"}
                                """.formatted(presentationId, prompt)))
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

    @TestConfiguration
    static class StorageTestConfig {
        @Bean
        @Primary
        SwitchableObjectStorageClient switchableObjectStorageClient(CosStorageProperties properties) {
            return new SwitchableObjectStorageClient(properties);
        }
    }

    static class SwitchableObjectStorageClient implements ObjectStorageClient {
        private final CosStorageProperties properties;
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private final Map<String, byte[]> objectBytes = new ConcurrentHashMap<>();
        private volatile boolean failUploads;

        SwitchableObjectStorageClient(CosStorageProperties properties) {
            this.properties = properties;
        }

        void failUploads(boolean failUploads) {
            this.failUploads = failUploads;
        }

        @Override
        public PresignedUpload createPresignedUpload(String objectKey, String contentType, Duration ttl) {
            return new PresignedUpload(objectKey, publicUrl(objectKey) + "?mockUploadToken=true", Instant.now().plus(ttl));
        }

        @Override
        public StoredObject putObject(String objectKey, String contentType, byte[] content) {
            if (failUploads) {
                throw new ObjectStorageException("Switchable mock COS upload failed");
            }
            StoredObject storedObject = new StoredObject(objectKey, publicUrl(objectKey), sha256(content));
            objects.put(objectKey, storedObject);
            objectBytes.put(objectKey, content.clone());
            return storedObject;
        }

        @Override
        public byte[] getObjectBytes(String objectKey) {
            byte[] content = objectBytes.get(objectKey);
            if (content == null) {
                throw new ObjectStorageException("Switchable mock COS object not found");
            }
            return content.clone();
        }

        @Override
        public String publicUrl(String objectKey) {
            return properties.publicBaseUrl().replaceAll("/$", "") + "/" + objectKey;
        }

        private String sha256(byte[] content) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (Exception exception) {
                return Integer.toHexString(content.length);
            }
        }
    }
}
