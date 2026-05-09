package com.pptagent.agent.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.image.ImageAssetEntity;
import com.pptagent.domain.image.ImageSourceType;
import com.pptagent.domain.image.ImageSuitabilityStatus;
import com.pptagent.domain.image.repository.ImageAssetRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AgentImageService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final ImageAssetRepository imageAssetRepository;
    private final ObjectMapper objectMapper;

    public AgentImageService(ImageAssetRepository imageAssetRepository, ObjectMapper objectMapper) {
        this.imageAssetRepository = imageAssetRepository;
        this.objectMapper = objectMapper;
    }

    public ArrayNode searchCandidates(String topic, String sourceUrl, Integer limit) {
        String normalizedTopic = requireTopic(topic);
        int effectiveLimit = effectiveLimit(limit);
        if (hasText(sourceUrl)) {
            return webCandidates(normalizedTopic, sourceUrl, effectiveLimit);
        }
        return mockCandidates(normalizedTopic, effectiveLimit);
    }

    public ObjectNode evaluate(JsonNode candidate) {
        if (candidate == null || !candidate.isObject()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Image candidate must be an object");
        }
        ObjectNode result = candidate.deepCopy();
        String topic = text(candidate, "topic", "general presentation");
        int width = intValue(candidate, "width", 0);
        int height = intValue(candidate, "height", 0);
        double score = qualityScore(width, height, text(candidate, "imageFormat", ""));
        boolean suitable = score >= 0.65d;

        ArrayNode tags = objectMapper.createArrayNode();
        for (String word : topic.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (!word.isBlank() && tags.size() < 4) {
                tags.add(word);
            }
        }
        tags.add("ppt-ready");
        result.set("tags", tags);
        result.put("description", "Mock VLM summary for PPT image candidate about " + topic + ".");
        result.put("scene", sceneFor(topic));
        result.put("qualityScore", score);
        result.put("suitable", suitable);
        result.put("suitabilityStatus", suitable ? ImageSuitabilityStatus.SUITABLE.name() : ImageSuitabilityStatus.REVIEW_REQUIRED.name());
        result.put("vlmMock", true);
        return result;
    }

    @Transactional
    public ObjectNode saveAsset(Long ownerUserId, JsonNode image) {
        if (ownerUserId == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "ownerUserId is required to save image asset");
        }
        ObjectNode evaluated = hasEvaluation(image) ? image.deepCopy() : evaluate(image);
        ImageAssetEntity asset = new ImageAssetEntity(
                ownerUserId,
                sourceType(text(evaluated, "sourceType", ImageSourceType.MOCK_SEARCH.name())),
                requiredText(evaluated, "sourceUrl"),
                text(evaluated, "publicUrl", requiredText(evaluated, "sourceUrl")),
                text(evaluated, "licenseNote", "License must be reviewed before production use"),
                intValue(evaluated, "width", 0),
                intValue(evaluated, "height", 0),
                text(evaluated, "imageFormat", "unknown"),
                json(evaluated.get("tags"), "[]"),
                text(evaluated, "description", ""),
                text(evaluated, "scene", ""),
                doubleValue(evaluated, "qualityScore", 0.0d),
                suitabilityStatus(text(evaluated, "suitabilityStatus", ImageSuitabilityStatus.REVIEW_REQUIRED.name())),
                booleanValue(evaluated, "suitable", false),
                metadataJson(evaluated)
        );
        imageAssetRepository.insert(asset);
        ObjectNode response = toJson(asset);
        response.set("evaluation", evaluated);
        return response;
    }

    @Transactional(readOnly = true)
    public ArrayNode listAssets(Long ownerUserId) {
        ArrayNode assets = objectMapper.createArrayNode();
        for (ImageAssetEntity asset : imageAssetRepository.findByOwnerUserId(ownerUserId)) {
            assets.add(toJson(asset));
        }
        return assets;
    }

    public ObjectNode toJson(ImageAssetEntity asset) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", asset.getId());
        node.put("ownerUserId", asset.getOwnerUserId());
        node.put("sourceType", asset.getSourceType().name());
        node.put("sourceUrl", asset.getSourceUrl());
        node.put("publicUrl", asset.getPublicUrl());
        node.put("licenseNote", asset.getLicenseNote());
        node.put("width", asset.getWidth());
        node.put("height", asset.getHeight());
        node.put("imageFormat", asset.getImageFormat());
        node.set("tags", parseJson(asset.getTagsJson(), objectMapper.createArrayNode()));
        node.put("description", asset.getDescriptionText());
        node.put("scene", asset.getSceneText());
        node.put("qualityScore", asset.getQualityScore());
        node.put("suitabilityStatus", asset.getSuitabilityStatus().name());
        node.put("suitable", asset.isSuitableForPpt());
        node.set("metadata", parseJson(asset.getMetadataJson(), objectMapper.createObjectNode()));
        return node;
    }

    private ArrayNode mockCandidates(String topic, int limit) {
        ArrayNode candidates = objectMapper.createArrayNode();
        String slug = slug(topic);
        for (int index = 1; index <= limit; index++) {
            candidates.add(candidate(
                    topic,
                    ImageSourceType.MOCK_SEARCH,
                    "https://mock-images.example.test/%s/source-%d".formatted(slug, index),
                    "https://mock-images.example.test/%s/%d.jpg".formatted(slug, index),
                    "Mock candidate image for Phase 07 tests; replace with licensed provider before production use",
                    1280 + (index * 80),
                    720 + (index * 40),
                    "jpg"
            ));
        }
        return candidates;
    }

    private ArrayNode webCandidates(String topic, String sourceUrl, int limit) {
        if (sourceUrl.contains("example.test") || sourceUrl.contains("mock.local")) {
            ArrayNode candidates = objectMapper.createArrayNode();
            candidates.add(candidate(topic, ImageSourceType.WEB_PAGE, sourceUrl,
                    sourceUrl.replaceAll("/+$", "") + "/hero.jpg",
                    "Mock webpage image; source license must be reviewed",
                    1440, 900, "jpg"));
            candidates.add(candidate(topic, ImageSourceType.WEB_PAGE, sourceUrl,
                    sourceUrl.replaceAll("/+$", "") + "/chart.png",
                    "Mock webpage image; source license must be reviewed",
                    1200, 800, "png"));
            return trim(candidates, limit);
        }
        try {
            Document document = Jsoup.connect(sourceUrl).timeout(3000).get();
            ArrayNode candidates = objectMapper.createArrayNode();
            for (Element image : document.select("img[src]")) {
                String imageUrl = image.absUrl("src");
                if (imageUrl.isBlank()) {
                    continue;
                }
                candidates.add(candidate(topic, ImageSourceType.WEB_PAGE, sourceUrl, imageUrl,
                        "Source page license must be reviewed before production use",
                        intAttr(image, "width"), intAttr(image, "height"), format(imageUrl)));
                if (candidates.size() >= limit) {
                    break;
                }
            }
            return candidates;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Image candidate page fetch failed");
        }
    }

    private ObjectNode candidate(
            String topic,
            ImageSourceType sourceType,
            String sourceUrl,
            String publicUrl,
            String licenseNote,
            int width,
            int height,
            String imageFormat
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("topic", topic);
        node.put("sourceType", sourceType.name());
        node.put("sourceUrl", sourceUrl);
        node.put("publicUrl", publicUrl);
        node.put("licenseNote", licenseNote);
        node.put("width", width);
        node.put("height", height);
        node.put("imageFormat", imageFormat);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("phase", "07");
        metadata.put("mock", sourceType == ImageSourceType.MOCK_SEARCH);
        node.set("metadata", metadata);
        return node;
    }

    private ArrayNode trim(ArrayNode source, int limit) {
        ArrayNode target = objectMapper.createArrayNode();
        for (int index = 0; index < source.size() && index < limit; index++) {
            target.add(source.get(index));
        }
        return target;
    }

    private String metadataJson(ObjectNode evaluated) {
        ObjectNode metadata = objectMapper.createObjectNode();
        JsonNode existing = evaluated.get("metadata");
        if (existing != null && existing.isObject()) {
            metadata.setAll((ObjectNode) existing);
        }
        metadata.put("vlmMock", booleanValue(evaluated, "vlmMock", true));
        return json(metadata, "{}");
    }

    private JsonNode parseJson(String value, JsonNode fallback) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private boolean hasEvaluation(JsonNode image) {
        return image != null && image.has("qualityScore") && image.has("tags") && image.has("suitabilityStatus");
    }

    private ImageSourceType sourceType(String value) {
        try {
            return ImageSourceType.valueOf(value);
        } catch (Exception exception) {
            return ImageSourceType.MOCK_SEARCH;
        }
    }

    private ImageSuitabilityStatus suitabilityStatus(String value) {
        try {
            return ImageSuitabilityStatus.valueOf(value);
        } catch (Exception exception) {
            return ImageSuitabilityStatus.REVIEW_REQUIRED;
        }
    }

    private String requireTopic(String topic) {
        if (!hasText(topic)) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "topic is required");
        }
        return topic.trim();
    }

    private int effectiveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        return value;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : fallback;
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() ? value.asInt() : fallback;
    }

    private double doubleValue(JsonNode node, String field, double fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private boolean booleanValue(JsonNode node, String field, boolean fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String json(JsonNode node, String fallback) {
        try {
            return node == null ? fallback : objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private double qualityScore(int width, int height, String format) {
        double resolutionScore = width >= 1200 && height >= 700 ? 0.78d : 0.58d;
        double formatBonus = List.of("jpg", "jpeg", "png", "webp").contains(format.toLowerCase(Locale.ROOT)) ? 0.12d : 0.02d;
        return Math.min(0.98d, Math.round((resolutionScore + formatBonus) * 100.0d) / 100.0d);
    }

    private String sceneFor(String topic) {
        return topic.toLowerCase(Locale.ROOT).contains("data") ? "data visualization" : "presentation illustration";
    }

    private int intAttr(Element element, String attr) {
        try {
            return Integer.parseInt(element.attr(attr));
        } catch (Exception exception) {
            return 0;
        }
    }

    private String format(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        List<String> formats = new ArrayList<>(List.of("webp", "png", "jpg", "jpeg", "gif"));
        for (String format : formats) {
            if (path.contains("." + format)) {
                return format;
            }
        }
        return "unknown";
    }

    private String slug(String topic) {
        String slug = topic.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "topic" : slug;
    }
}
