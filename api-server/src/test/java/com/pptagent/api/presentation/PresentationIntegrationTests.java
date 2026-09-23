package com.pptagent.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import com.pptagent.domain.auth.repository.ProjectMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "ppt.jwt.access-token-ttl=30s")
@AutoConfigureMockMvc
class PresentationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private SlideElementRepository slideElementRepository;

    @Test
    void presentationDefaultsOwnerMembershipAndReadPermissionsWork() throws Exception {
        String ownerToken = registerAndToken("ppt-owner");
        String otherToken = registerAndToken("ppt-other");

        JsonNode created = createPresentation(ownerToken, "Quarterly Review");
        long presentationId = created.at("/data/id").asLong();
        long ownerUserId = created.at("/data/ownerUserId").asLong();

        assertThat(created.at("/data/pageWidth").asInt()).isEqualTo(1280);
        assertThat(created.at("/data/pageHeight").asInt()).isEqualTo(720);
        assertThat(created.at("/data/theme").isObject()).isTrue();
        assertThat(projectMemberRepository.findByProjectIdAndUserId(presentationId, ownerUserId)).isPresent();

        mockMvc.perform(get("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Quarterly Review"));

        mockMvc.perform(get("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/presentations/" + presentationId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void presentationMembersCanBeSharedDowngradedAndRemoved() throws Exception {
        RegisteredUser owner = registerUser("ppt-share-owner");
        RegisteredUser member = registerUser("ppt-share-member");
        RegisteredUser outsider = registerUser("ppt-share-outsider");
        long presentationId = createPresentation(owner.token(), "Shared Deck").at("/data/id").asLong();

        mockMvc.perform(get("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isForbidden());

        sharePresentationMember(owner.token(), presentationId, member.email(), false)
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/presentations/" + presentationId + "/preview")
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Viewer edit","pageWidth":1280,"pageHeight":720}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/presentations/" + presentationId + "/slides")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Viewer slide"}
                                """))
                .andExpect(status().isForbidden());

        sharePresentationMember(owner.token(), presentationId, member.email(), true)
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Editor edit","pageWidth":1280,"pageHeight":720}
                                """))
                .andExpect(status().isOk());
        createSlide(member.token(), presentationId, null, "Editor slide");

        sharePresentationMember(member.token(), presentationId, outsider.email(), false)
                .andExpect(status().isForbidden());
        removePresentationMember(member.token(), presentationId, outsider.email())
                .andExpect(status().isForbidden());

        sharePresentationMember(owner.token(), presentationId, member.email(), false)
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Downgraded edit","pageWidth":1280,"pageHeight":720}
                                """))
                .andExpect(status().isForbidden());

        sharePresentationMember(owner.token(), presentationId, owner.email(), false)
                .andExpect(status().isBadRequest());
        removePresentationMember(owner.token(), presentationId, owner.email())
                .andExpect(status().isBadRequest());

        removePresentationMember(owner.token(), presentationId, member.email())
                .andExpect(status().isNoContent());
        Long memberUserId = userId(member.email());
        assertThat(projectMemberRepository.findByProjectIdAndUserId(presentationId, memberUserId)).isEmpty();
        mockMvc.perform(get("/api/presentations/" + presentationId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isForbidden());

        var removedMember = projectMemberRepository.findAnyByProjectIdAndUserId(presentationId, memberUserId)
                .orElseThrow();
        assertThat(removedMember.getIsDeleted()).isEqualTo("1");
        sharePresentationMember(owner.token(), presentationId, member.email(), true)
                .andExpect(status().isOk());
        assertThat(projectMemberRepository.findByProjectIdAndUserId(presentationId, memberUserId)).isPresent();
    }

    @Test
    void slidesCanBeInsertedMovedDeletedAndReordered() throws Exception {
        String token = registerAndToken("ppt-slides");
        long presentationId = createPresentation(token, "Slide Order").at("/data/id").asLong();

        JsonNode firstResponse = createSlide(token, presentationId, null, "First");
        long firstSlideId = firstResponse.at("/data/slides/0/id").asLong();
        JsonNode insertResponse = createSlide(token, presentationId, 1, "Inserted");
        long insertedSlideId = insertResponse.at("/data/slides/0/id").asLong();

        assertThat(insertResponse.at("/data/slides/0/pageOrder").asInt()).isEqualTo(1);
        assertThat(insertResponse.at("/data/slides/0/id").asLong()).isEqualTo(insertedSlideId);
        assertThat(insertResponse.at("/data/slides/1/pageOrder").asInt()).isEqualTo(2);
        assertThat(insertResponse.at("/data/slides/1/id").asLong()).isEqualTo(firstSlideId);

        JsonNode movedResponse = objectMapper.readTree(mockMvc.perform(patch("/api/slides/" + firstSlideId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageOrder":1,"title":"First moved","background":{"color":"white"},"speakerNotes":"notes"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(movedResponse.at("/data/slides/0/id").asLong()).isEqualTo(firstSlideId);
        assertThat(movedResponse.at("/data/slides/1/id").asLong()).isEqualTo(insertedSlideId);

        long elementId = createElement(token, firstSlideId, """
                {
                  "type":"TEXT",
                  "x":1,
                  "y":1,
                  "width":100,
                  "height":40,
                  "content":{"text":"Deleted with slide"},
                  "style":{}
                }
                """).at("/data/id").asLong();

        JsonNode afterDelete = objectMapper.readTree(mockMvc.perform(delete("/api/slides/" + firstSlideId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(afterDelete.at("/data/slides").size()).isEqualTo(1);
        assertThat(afterDelete.at("/data/slides/0/id").asLong()).isEqualTo(insertedSlideId);
        assertThat(afterDelete.at("/data/slides/0/pageOrder").asInt()).isEqualTo(1);
        assertThat(slideRepository.findWithPresentationById(firstSlideId)).isEmpty();
        assertThat(slideRepository.selectById(firstSlideId).getIsDeleted()).isEqualTo("1");
        assertThat(slideElementRepository.findWithSlideAndPresentationById(elementId)).isEmpty();
        assertThat(slideElementRepository.selectById(elementId).getIsDeleted()).isEqualTo("1");
    }

    @Test
    void elementsTrackPageOrderZIndexInitialPresenceImagesAndVersions() throws Exception {
        String token = registerAndToken("ppt-elements");
        long presentationId = createPresentation(token, "Element Model").at("/data/id").asLong();
        long slideId = createSlide(token, presentationId, null, "Page").at("/data/slides/0/id").asLong();
        long fileId = createAvailableImageFile(token);

        JsonNode textElement = createElement(token, slideId, """
                {
                  "type":"TEXT",
                  "x":10,
                  "y":12,
                  "width":300,
                  "height":80,
                  "zIndex":9,
                  "content":{"text":"Original text"},
                  "style":{"fontSize":24}
                }
                """);
        long textElementId = textElement.at("/data/id").asLong();

        JsonNode imageElement = createElement(token, slideId, """
                {
                  "type":"IMAGE",
                  "x":40,
                  "y":50,
                  "width":240,
                  "height":160,
                  "zIndex":1,
                  "pageOrder":1,
                  "initiallyPresent":true,
                  "content":{"fileAssetId":%d},
                  "style":{"fit":"cover"}
                }
                """.formatted(fileId));
        long imageElementId = imageElement.at("/data/id").asLong();

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides/0/elements/0/id").asLong()).isEqualTo(imageElementId);
        assertThat(preview.at("/data/slides/0/elements/0/pageOrder").asInt()).isEqualTo(1);
        assertThat(preview.at("/data/slides/0/elements/0/initiallyPresent").asBoolean()).isTrue();
        assertThat(preview.at("/data/slides/0/elements/0/content/publicUrl").asText()).contains("https://cos.example.test/");
        assertThat(preview.at("/data/slides/0/elements/1/id").asLong()).isEqualTo(textElementId);
        assertThat(preview.at("/data/slides/0/elements/1/zIndex").asInt()).isEqualTo(9);
        assertThat(preview.at("/data/slides/0/elements/1/pageOrder").asInt()).isEqualTo(2);

        mockMvc.perform(patch("/api/elements/" + textElementId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"TEXT",
                                  "x":10,
                                  "y":12,
                                  "width":300,
                                  "height":80,
                                  "zIndex":100,
                                  "content":{"text":"Original text"},
                                  "style":{"fontSize":24},
                                  "expectedRevision":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageOrder").value(2))
                .andExpect(jsonPath("$.data.zIndex").value(100));

        JsonNode afterZIndexOnly = readPreview(token, presentationId);
        assertThat(afterZIndexOnly.at("/data/slides/0/elements/0/id").asLong()).isEqualTo(imageElementId);
        assertThat(afterZIndexOnly.at("/data/slides/0/elements/1/id").asLong()).isEqualTo(textElementId);

        JsonNode version = objectMapper.readTree(mockMvc.perform(post("/api/presentations/" + presentationId + "/versions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"checkpoint"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(version.at("/data/snapshot/slides/0/elements/1/content/text").asText()).isEqualTo("Original text");

        mockMvc.perform(patch("/api/elements/" + textElementId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"TEXT",
                                  "x":10,
                                  "y":12,
                                  "width":300,
                                  "height":80,
                                  "zIndex":100,
                                  "content":{"text":"Changed after snapshot"},
                                  "style":{"fontSize":24},
                                  "expectedRevision":2
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode oldVersion = objectMapper.readTree(mockMvc.perform(get("/api/presentations/" + presentationId + "/versions/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(oldVersion.at("/data/snapshot/slides/0/elements/1/content/text").asText()).isEqualTo("Original text");

        mockMvc.perform(delete("/api/elements/" + imageElementId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isNoContent());
        JsonNode afterElementDelete = readPreview(token, presentationId);
        assertThat(afterElementDelete.at("/data/slides/0/elements").size()).isEqualTo(1);
        assertThat(slideElementRepository.selectById(imageElementId).getIsDeleted()).isEqualTo("1");
    }

    @Test
    void batchElementFailuresRollbackAllOperations() throws Exception {
        String token = registerAndToken("ppt-batch");
        long presentationId = createPresentation(token, "Batch").at("/data/id").asLong();
        long slideId = createSlide(token, presentationId, null, "Batch Page").at("/data/slides/0/id").asLong();
        createElement(token, slideId, """
                {
                  "type":"TEXT",
                  "x":1,
                  "y":1,
                  "width":100,
                  "height":40,
                  "content":{"text":"Existing"},
                  "style":{}
                }
                """);

        mockMvc.perform(patch("/api/slides/" + slideId + "/elements/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operations":[
                                    {
                                      "action":"ADD",
                                      "type":"TEXT",
                                      "x":2,
                                      "y":2,
                                      "width":100,
                                      "height":40,
                                      "content":{"text":"Should rollback"},
                                      "style":{}
                                    },
                                    {
                                      "action":"ADD",
                                      "type":"TEXT",
                                      "x":3,
                                      "y":3,
                                      "width":-1,
                                      "height":40,
                                      "content":{"text":"Invalid"},
                                      "style":{}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        JsonNode preview = readPreview(token, presentationId);
        assertThat(preview.at("/data/slides/0/elements").size()).isEqualTo(1);
        assertThat(preview.at("/data/slides/0/elements/0/content/text").asText()).isEqualTo("Existing");
    }

    @Test
    void aiEditsRespectLocksAndRejectStaleOrPartiallyConflictingCommands() throws Exception {
        String token = registerAndToken("ppt-ai-edit");
        long presentationId = createPresentation(token, "AI Edit Protection").at("/data/id").asLong();
        long slideId = createSlide(token, presentationId, null, "Page").at("/data/slides/0/id").asLong();
        long firstId = createElement(token, slideId, """
                {"type":"TEXT","x":1,"y":1,"width":100,"height":40,"content":{"text":"First"},"style":{}}
                """).at("/data/id").asLong();
        long secondId = createElement(token, slideId, """
                {"type":"TEXT","x":2,"y":2,"width":100,"height":40,"content":{"text":"Second"},"style":{}}
                """).at("/data/id").asLong();

        mockMvc.perform(put("/api/elements/" + secondId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1,\"locked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.revision").value(2));

        mockMvc.perform(patch("/api/elements/" + secondId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEXT","x":2,"y":2,"width":100,"height":40,
                                 "content":{"text":"human overwrite"},"style":{},"expectedRevision":2}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/presentations/" + presentationId + "/ai-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"edits":[
                                  {"elementId":%d,"expectedRevision":1,"content":{"text":"AI changed first"}},
                                  {"elementId":%d,"expectedRevision":2,"content":{"text":"AI changed locked"}}
                                ]}
                                """.formatted(firstId, secondId)))
                .andExpect(status().isConflict());
        assertThat(slideElementRepository.selectById(firstId).getContentJson()).contains("First");
        assertThat(slideElementRepository.selectById(secondId).getContentJson()).contains("Second");

        mockMvc.perform(post("/api/presentations/" + presentationId + "/ai-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"edits":[{"elementId":%d,"expectedRevision":1,"content":{"text":"stale"}}]}
                                """.formatted(firstId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revision").value(2));

        mockMvc.perform(put("/api/elements/" + secondId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":2,\"locked\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(3));

        mockMvc.perform(post("/api/presentations/" + presentationId + "/ai-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"edits":[{"elementId":%d,"expectedRevision":2,"content":{"text":"stale after lock"}}]}
                                """.formatted(secondId)))
                .andExpect(status().isConflict());
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

    private JsonNode createSlide(String token, long presentationId, Integer pageOrder, String title) throws Exception {
        String pageOrderJson = pageOrder == null ? "" : "\"pageOrder\":%d,".formatted(pageOrder);
        return objectMapper.readTree(mockMvc.perform(post("/api/presentations/" + presentationId + "/slides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s"title":"%s","background":{"color":"white"},"speakerNotes":""}
                                """.formatted(pageOrderJson, title)))
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
                                  "originalFilename":"figure.png",
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

    private org.springframework.test.web.servlet.ResultActions sharePresentationMember(
            String token,
            long presentationId,
            String email,
            boolean canEdit
    ) throws Exception {
        return mockMvc.perform(post("/api/presentations/" + presentationId + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","canEdit":%s}
                        """.formatted(email, canEdit)));
    }

    private org.springframework.test.web.servlet.ResultActions removePresentationMember(
            String token,
            long presentationId,
            String email
    ) throws Exception {
        return mockMvc.perform(delete("/api/presentations/" + presentationId + "/members")
                .param("email", email)
                .header("Authorization", "Bearer " + token));
    }

    private String registerAndToken(String prefix) throws Exception {
        return registerUser(prefix).token();
    }

    private RegisteredUser registerUser(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "%s-%s@example.test".formatted(prefix, suffix);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@example.test","username":"%s%s","password":"StrongPass123"}
                                """.formatted(prefix, suffix, prefix.replace("-", ""), suffix.substring(0, 8))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new RegisteredUser(email, objectMapper.readTree(response).at("/data/accessToken").asText());
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private record RegisteredUser(String email, String token) {
    }
}
