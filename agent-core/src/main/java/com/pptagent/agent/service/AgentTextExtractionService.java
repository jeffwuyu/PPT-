package com.pptagent.agent.service;

import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.file.FileAssetEntity;
import com.pptagent.domain.file.FileType;
import com.pptagent.infrastructure.storage.ObjectStorageClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AgentTextExtractionService {

    private static final int MAX_SOURCE_CHARS = 12000;

    private final ObjectStorageClient objectStorageClient;

    public AgentTextExtractionService(ObjectStorageClient objectStorageClient) {
        this.objectStorageClient = objectStorageClient;
    }

    public String extract(FileAssetEntity asset) {
        if (asset.getFileType() != FileType.SOURCE_TEXT) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent source file must be a text document");
        }
        byte[] bytes = objectStorageClient.getObjectBytes(asset.getObjectKey());
        String extension = extension(asset.getOriginalFilename());
        String contentType = asset.getContentType() == null ? "" : asset.getContentType().toLowerCase(Locale.ROOT);
        String text;
        if ("docx".equals(extension) || contentType.contains("wordprocessingml")) {
            text = extractDocx(bytes);
        } else if ("pdf".equals(extension) || contentType.contains("pdf")) {
            text = extractPdf(bytes);
        } else {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        return normalize(text);
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (!paragraph.getText().isBlank()) {
                    builder.append(paragraph.getText()).append('\n');
                }
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Failed to extract Word text");
        }
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Failed to extract PDF text");
        }
    }

    private String normalize(String text) {
        String normalized = text == null ? "" : text.replace("\u0000", "").trim();
        if (normalized.length() > MAX_SOURCE_CHARS) {
            return normalized.substring(0, MAX_SOURCE_CHARS);
        }
        return normalized;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 && index < filename.length() - 1
                ? filename.substring(index + 1).toLowerCase(Locale.ROOT)
                : "";
    }
}
