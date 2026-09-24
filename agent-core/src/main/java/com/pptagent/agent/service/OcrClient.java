package com.pptagent.agent.service;

import java.util.List;

public interface OcrClient {
    OcrDocument recognize(byte[] content, String filename);

    record OcrDocument(String provider, String model, String requestId, List<OcrPage> pages) { }
    record OcrPage(int page, String text, double confidence) { }
}
