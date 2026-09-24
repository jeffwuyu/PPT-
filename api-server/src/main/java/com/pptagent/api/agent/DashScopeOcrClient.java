package com.pptagent.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.agent.service.OcrClient;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashScopeOcrClient implements OcrClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public DashScopeOcrClient(ObjectMapper mapper,
                              @Value("${ppt.agent.ocr.base-url:${PPT_AGENT_OCR_BASE_URL:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}}") String baseUrl,
                              @Value("${ppt.agent.ocr.api-key:${PPT_AGENT_OCR_API_KEY:${DASHSCOPE_API_KEY:}}}") String apiKey,
                              @Value("${ppt.agent.ocr.model:${PPT_AGENT_OCR_MODEL:qwen-vl-ocr}}") String model,
                              @Value("${ppt.agent.ocr.timeout-ms:${PPT_AGENT_OCR_TIMEOUT_MS:30000}}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
    }

    @Override
    public OcrDocument recognize(byte[] content, String filename) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "OCR_REQUIRED");
        }
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("model", model,
                    "input", java.util.Map.of("filename", filename, "contentBase64", java.util.Base64.getEncoder().encodeToString(content))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl)).timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "OCR provider temporarily unavailable");
            }
            if (response.statusCode() / 100 != 2) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "OCR provider rejected document");
            }
            JsonNode root = mapper.readTree(response.body());
            String requestId = root.path("request_id").asText(root.path("requestId").asText(""));
            List<OcrPage> pages = new ArrayList<>();
            for (JsonNode page : root.path("pages")) {
                pages.add(new OcrPage(page.path("page").asInt(pages.size() + 1),
                        page.path("text").asText(""), page.path("confidence").asDouble(0.0)));
            }
            return new OcrDocument("dashscope", model, requestId, pages);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "OCR provider request failed");
        }
    }
}
