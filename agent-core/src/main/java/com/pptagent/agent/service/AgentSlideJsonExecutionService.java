package com.pptagent.agent.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.agent.ai.AgentAiClient;
import com.pptagent.domain.agent.AgentArtifactRefEntity;
import com.pptagent.domain.agent.AgentArtifactType;
import com.pptagent.domain.agent.AgentCheckpointEntity;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentSourceEntity;
import com.pptagent.domain.agent.AgentSourceType;
import com.pptagent.domain.agent.repository.AgentArtifactRefRepository;
import com.pptagent.domain.agent.repository.AgentSourceRepository;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileAssetStatus;
import com.pptagent.domain.file.FileType;
import com.pptagent.domain.file.repository.FileAssetRepository;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideElementType;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.PresentationRepository;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AgentSlideJsonExecutionService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_BODY_LENGTH = 520;
    private static final String ACCENT_COLOR = "#2563eb";
    private static final String TEXT_COLOR = "#111827";
    private static final String MUTED_COLOR = "#4b5563";

    private final AgentSourceRepository sourceRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PresentationRepository presentationRepository;
    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;
    private final AgentArtifactRefRepository artifactRefRepository;
    private final AgentJsonService jsonService;
    private final AgentAiClient aiClient;

    public AgentSlideJsonExecutionService(
            AgentSourceRepository sourceRepository,
            FileAssetRepository fileAssetRepository,
            PresentationRepository presentationRepository,
            SlideRepository slideRepository,
            SlideElementRepository slideElementRepository,
            AgentArtifactRefRepository artifactRefRepository,
            AgentJsonService jsonService,
            AgentAiClient aiClient
    ) {
        this.sourceRepository = sourceRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.presentationRepository = presentationRepository;
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
        this.artifactRefRepository = artifactRefRepository;
        this.jsonService = jsonService;
        this.aiClient = aiClient;
    }

    public Long createSlideArtifact(AgentJobEntity job, AgentCheckpointEntity checkpoint, int iteration, String titleOverride) {
        ObjectNode page = jsonService.objectNode();
        page.put("title", titleOverride == null ? checkpoint.getTitle() : titleOverride);
        page.put("objective", checkpoint.getObjective());
        page.putArray("keyPoints").add(checkpoint.getObjective());
        page.putArray("sourceBlockIds");
        return createSlideArtifact(job, checkpoint, iteration, titleOverride, page);
    }

    public Long createSlideArtifact(AgentJobEntity job, AgentCheckpointEntity checkpoint, int iteration,
                                    String titleOverride, JsonNode approvedPage) {
        PresentationEntity presentation = presentationRepository.selectById(job.getPresentationId());
        if (presentation == null) {
            throw new SlideValidationException("Presentation not found while generating slide JSON");
        }
        DraftSlide draft = buildDraft(job, checkpoint, presentation, iteration, titleOverride, approvedPage);
        validate(draft, presentation, job, checkpoint, iteration);

        int pageOrder = (int) slideRepository.countByPresentationId(job.getPresentationId()) + 1;
        SlideEntity slide = new SlideEntity(
                presentation,
                pageOrder,
                draft.title(),
                draft.backgroundJson(),
                draft.speakerNotes()
        );
        slideRepository.insert(slide);
        artifactRefRepository.insert(new AgentArtifactRefEntity(job.getId(), checkpoint.getId(), AgentArtifactType.SLIDE, slide.getId(), "CREATE", jsonService.emptyObject()));

        int order = 1;
        for (DraftElement element : draft.elements()) {
            SlideElementEntity entity = new SlideElementEntity(
                    slide,
                    element.elementKey(),
                    element.type(),
                    element.x(),
                    element.y(),
                    element.width(),
                    element.height(),
                    0,
                    element.zIndex(),
                    order++,
                    true,
                    element.contentJson(),
                    element.styleJson()
            );
            slideElementRepository.insert(entity);
            artifactRefRepository.insert(new AgentArtifactRefEntity(job.getId(), checkpoint.getId(), AgentArtifactType.ELEMENT, entity.getId(), "CREATE", jsonService.emptyObject()));
        }
        return slide.getId();
    }

    private DraftSlide buildDraft(
            AgentJobEntity job,
            AgentCheckpointEntity checkpoint,
            PresentationEntity presentation,
            int iteration,
            String titleOverride,
            JsonNode approvedPage
    ) {
        int width = presentation.getPageWidth();
        int height = presentation.getPageHeight();
        String title = normalizeTitle(firstNonBlank(approvedPage.path("title").asText(null), titleOverride, checkpoint.getTitle()));
        List<String> keyPoints = readKeyPoints(approvedPage);
        List<SourceBlock> sources = readApprovedSources(job.getId(), approvedPage.path("sourceBlockIds"));
        String input = jsonService.toJson(java.util.Map.of(
                "page", approvedPage,
                "sources", sources.stream().map(source -> java.util.Map.of(
                        "blockId", source.blockId(), "text", source.text(), "locator", source.locator())).toList()
        ));
        JsonNode generated = jsonService.fromJson(aiClient.generatePageContent(input));
        String body = generated.path("body").asText("").strip();
        List<String> covered = readTextArray(generated.path("keyPointsCovered"));
        List<String> citedSourceIds = readTextArray(generated.path("sourceBlockIds"));
        if (body.isBlank() || !covered.containsAll(keyPoints) || !containsEveryKeyPoint(body, keyPoints)) {
            throw new SlideValidationException("Generated page does not cover every approved key point");
        }
        java.util.Set<String> allowedSourceIds = sources.stream().map(SourceBlock::blockId).collect(java.util.stream.Collectors.toSet());
        if (!allowedSourceIds.containsAll(citedSourceIds)) {
            throw new SlideValidationException("Generated page cites a source block outside the approved outline");
        }
        body = normalizeBody(body);
        ImageChoice image = chooseImage(job, checkpoint);

        ObjectNode background = jsonService.objectNode();
        background.put("color", "#f8fafc");
        background.put("accentColor", ACCENT_COLOR);

        String keyPrefix = "agent-%d-cp-%d".formatted(job.getId(), checkpoint.getCheckpointOrder());
        DraftElement titleElement = textElement(
                keyPrefix + "-title",
                56,
                48,
                Math.max(320, width - 112),
                96,
                3,
                title,
                36,
                TEXT_COLOR,
                true
        );
        DraftElement bodyElement = textElement(
                keyPrefix + "-body",
                72,
                178,
                Math.max(280, Math.round(width * 0.48d)),
                Math.max(180, height - 260),
                2,
                body,
                24,
                MUTED_COLOR,
                false
        );
        DraftElement imageElement = imageElement(
                keyPrefix + "-image",
                Math.round(width * 0.60d),
                176,
                Math.max(260, Math.round(width * 0.32d)),
                Math.max(220, Math.round(height * 0.52d)),
                1,
                image
        );
        return new DraftSlide(
                title,
                jsonService.toJson(background),
                "Generated by Agent checkpoint %d. Objective: %s".formatted(checkpoint.getCheckpointOrder(), body),
                List.of(titleElement, bodyElement, imageElement)
        );
    }

    private DraftElement textElement(
            String key,
            double x,
            double y,
            double width,
            double height,
            int zIndex,
            String text,
            int fontSize,
            String color,
            boolean bold
    ) {
        ObjectNode content = jsonService.objectNode();
        content.put("text", text);

        ObjectNode style = jsonService.objectNode();
        style.put("fontSize", fontSize);
        style.put("color", color);
        style.put("fontWeight", bold ? "700" : "400");
        style.put("lineHeight", 1.25d);
        style.put("themeAccent", ACCENT_COLOR);

        return new DraftElement(key, SlideElementType.TEXT, x, y, width, height, zIndex, jsonService.toJson(content), jsonService.toJson(style));
    }

    private DraftElement imageElement(
            String key,
            double x,
            double y,
            double width,
            double height,
            int zIndex,
            ImageChoice image
    ) {
        ObjectNode content = jsonService.objectNode();
        content.put("placeholder", image.placeholder());
        content.put("altText", image.altText());
        content.put("source", image.source());
        if (image.fileAssetId() != null) {
            content.put("fileAssetId", image.fileAssetId());
        }
        if (image.reason() != null && !image.reason().isBlank()) {
            content.put("fallbackReason", image.reason());
        }

        ObjectNode style = jsonService.objectNode();
        style.put("fit", "cover");
        style.put("borderRadius", 8);
        style.put("backgroundColor", image.placeholder() ? "#e5e7eb" : "#ffffff");
        style.put("borderColor", ACCENT_COLOR);
        style.put("themeAccent", ACCENT_COLOR);

        return new DraftElement(key, SlideElementType.IMAGE, x, y, width, height, zIndex, jsonService.toJson(content), jsonService.toJson(style));
    }

    private ImageChoice chooseImage(AgentJobEntity job, AgentCheckpointEntity checkpoint) {
        for (AgentSourceEntity source : sourceRepository.findByJobId(job.getId())) {
            if (source.getSourceType() != AgentSourceType.IMAGE || source.getFileAssetId() == null) {
                continue;
            }
            FileAssetEntity asset = fileAssetRepository.selectById(source.getFileAssetId());
            if (asset == null) {
                continue;
            }
            if ((asset.getFileType() == FileType.USER_IMAGE || asset.getFileType() == FileType.CRAWLED_IMAGE)
                    && asset.getStatus() == FileAssetStatus.AVAILABLE) {
                return new ImageChoice(asset.getId(), false, "IMAGE_ASSET", "Image for " + checkpoint.getTitle(), null);
            }
        }
        return new ImageChoice(null, true, "PLACEHOLDER", "Image placeholder for " + checkpoint.getTitle(), "No available USER_IMAGE or CRAWLED_IMAGE source");
    }

    private void validate(DraftSlide draft, PresentationEntity presentation, AgentJobEntity job, AgentCheckpointEntity checkpoint, int iteration) {
        if (job.getPromptText().toLowerCase(Locale.ROOT).contains("force_slide_validation_failure")) {
            throw new SlideValidationException("Forced slide validation failure");
        }
        if (job.getPromptText().toLowerCase(Locale.ROOT).contains("retry_slide_validation_once") && iteration == 1) {
            throw new SlideValidationException("Forced transient slide validation failure");
        }
        if (draft.title().isBlank() || draft.title().length() > MAX_TITLE_LENGTH) {
            throw new SlideValidationException("Slide title is empty or too long");
        }
        if (draft.speakerNotes().isBlank()) {
            throw new SlideValidationException("Slide speaker notes are required");
        }
        if (draft.elements().isEmpty()) {
            throw new SlideValidationException("Generated slide must contain elements");
        }
        for (DraftElement element : draft.elements()) {
            if (element.width() <= 0 || element.height() <= 0 || element.x() < 0 || element.y() < 0) {
                throw new SlideValidationException("Generated element has invalid dimensions");
            }
            if (element.x() + element.width() > presentation.getPageWidth() || element.y() + element.height() > presentation.getPageHeight()) {
                throw new SlideValidationException("Generated element is outside slide bounds");
            }
            if (element.type() == SlideElementType.TEXT) {
                String text = jsonService.fromJson(element.contentJson()).path("text").asText();
                if (text.isBlank() || text.length() > MAX_BODY_LENGTH) {
                    throw new SlideValidationException("Generated text element is empty or too long");
                }
            }
            if (!jsonService.fromJson(element.styleJson()).path("themeAccent").asText("").equals(ACCENT_COLOR)) {
                throw new SlideValidationException("Generated element style is not theme-consistent");
            }
        }
    }

    private String normalizeTitle(String value) {
        String title = value == null || value.isBlank() ? "Generated Slide" : value.strip();
        return abbreviate(title, MAX_TITLE_LENGTH);
    }

    private String normalizeBody(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private List<String> readKeyPoints(JsonNode page) {
        return readTextArray(page.path("keyPoints"));
    }

    private boolean containsEveryKeyPoint(String body, List<String> keyPoints) {
        for (String point : keyPoints) if (!body.contains(point)) return false;
        return true;
    }

    private List<String> readTextArray(JsonNode values) {
        if (!values.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        values.forEach(value -> {
            String text = value.asText("").trim();
            if (!text.isBlank()) result.add(text);
        });
        return result;
    }

    private List<SourceBlock> readApprovedSources(Long jobId, JsonNode approvedIds) {
        java.util.Set<String> allowed = new java.util.HashSet<>(readTextArray(approvedIds));
        if (allowed.isEmpty()) return List.of();
        List<SourceBlock> result = new java.util.ArrayList<>();
        for (AgentSourceEntity source : sourceRepository.findByJobId(jobId)) {
            if (source.getSourceType() != AgentSourceType.FILE) continue;
            JsonNode blocks = jsonService.fromJson(source.getMetadataJson()).path("blocks");
            if (!blocks.isArray()) continue;
            for (JsonNode block : blocks) {
                String blockId = block.path("blockId").asText("");
                if (allowed.contains(blockId)) {
                    result.add(new SourceBlock(blockId, block.path("text").asText(""), block.path("locator")));
                }
            }
        }
        if (result.size() != allowed.size()) {
            throw new SlideValidationException("Approved outline references a missing source block");
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "Generated Slide";
    }

    private String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static class SlideValidationException extends RuntimeException {
        public SlideValidationException(String message) {
            super(message);
        }
    }

    private record DraftSlide(String title, String backgroundJson, String speakerNotes, List<DraftElement> elements) {
    }

    private record DraftElement(
            String elementKey,
            SlideElementType type,
            double x,
            double y,
            double width,
            double height,
            int zIndex,
            String contentJson,
            String styleJson
    ) {
    }

    private record ImageChoice(Long fileAssetId, boolean placeholder, String source, String altText, String reason) {
    }

    private record SourceBlock(String blockId, String text, JsonNode locator) { }
}
