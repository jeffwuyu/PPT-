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
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentTextExtractionService {

    private final ObjectStorageClient objectStorageClient;

    public AgentTextExtractionService(ObjectStorageClient objectStorageClient) {
        this.objectStorageClient = objectStorageClient;
    }

    public String extract(FileAssetEntity asset) {
        return extractStructured(asset).blocks().stream()
                .map(SourceBlock::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    /**
     * Extract the complete source document without the old 12k character truncation.
     * Blocks retain a lightweight locator so outline generation can cite the source.
     */
    public ExtractedDocument extractStructured(FileAssetEntity asset) {
        if (asset.getFileType() != FileType.SOURCE_TEXT) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Agent source file must be a text document");
        }
        byte[] bytes = objectStorageClient.getObjectBytes(asset.getObjectKey());
        String extension = extension(asset.getOriginalFilename());
        String contentType = asset.getContentType() == null ? "" : asset.getContentType().toLowerCase(Locale.ROOT);
        validateMagic(bytes, extension, contentType);
        List<SourceBlock> blocks;
        if ("docx".equals(extension) || contentType.contains("wordprocessingml")) {
            blocks = extractDocxBlocks(bytes);
        } else if ("pdf".equals(extension) || contentType.contains("pdf")) {
            blocks = extractPdfBlocks(bytes);
        } else if ("md".equals(extension) || contentType.contains("markdown")) {
            blocks = extractMarkdownBlocks(new String(bytes, StandardCharsets.UTF_8));
        } else {
            blocks = extractPlainTextBlocks(new String(bytes, StandardCharsets.UTF_8));
        }
        if (blocks.isEmpty()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "EMPTY_DOCUMENT");
        }
        String sourceHash = sha256(bytes);
        return new ExtractedDocument(sourceHash, "parser-v2", List.copyOf(blocks), List.of());
    }

    private void validateMagic(byte[] bytes, String extension, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "DOCUMENT_CORRUPTED");
        }
        boolean pdf = "pdf".equals(extension) || contentType.contains("pdf");
        boolean docx = "docx".equals(extension) || contentType.contains("wordprocessingml");
        if (pdf && !startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "DOCUMENT_CORRUPTED");
        }
        // DOCX is an OOXML zip package. Checking the PK header catches MIME
        // spoofing before Apache POI or OCR work is started.
        if (docx && !startsWith(bytes, new byte[]{'P', 'K', 3, 4})) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "DOCUMENT_CORRUPTED");
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    public byte[] loadSourceBytes(FileAssetEntity asset) {
        return objectStorageClient.getObjectBytes(asset.getObjectKey());
    }

    private List<SourceBlock> extractDocxBlocks(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<SourceBlock> blocks = new ArrayList<>();
            int paragraphIndex = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (!paragraph.getText().isBlank()) {
                    blocks.add(new SourceBlock("paragraph-" + paragraphIndex, "paragraph", paragraph.getText().trim(),
                            java.util.Map.of("paragraphIndex", paragraphIndex)));
                }
                paragraphIndex++;
            }
            int tableIndex = 0;
            for (XWPFTable table : document.getTables()) {
                List<String> rows = table.getRows().stream()
                        .map(row -> String.join(" | ", row.getTableCells().stream().map(cell -> cell.getText().trim()).toList()))
                        .filter(row -> !row.isBlank())
                        .toList();
                if (!rows.isEmpty()) {
                    blocks.add(new SourceBlock("table-" + tableIndex, "table", String.join("\n", rows),
                            java.util.Map.of("tableIndex", tableIndex)));
                }
                tableIndex++;
            }
            return blocks;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Failed to extract Word text");
        }
    }

    private List<SourceBlock> extractPdfBlocks(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<SourceBlock> blocks = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (!text.isBlank()) {
                    blocks.add(new SourceBlock("page-" + page, "paragraph", text, java.util.Map.of("page", page)));
                }
            }
            if (blocks.isEmpty() && document.getNumberOfPages() > 0) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "OCR_REQUIRED");
            }
            return blocks;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Failed to extract PDF text");
        }
    }

    private List<SourceBlock> extractPlainTextBlocks(String text) {
        String normalized = text == null ? "" : text.replace("\u0000", "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SourceBlock> blocks = new ArrayList<>();
        String[] lines = normalized.split("\\R");
        int start = 1;
        StringBuilder paragraph = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank() && !paragraph.isEmpty()) {
                blocks.add(new SourceBlock("lines-" + start + "-" + i, "paragraph", paragraph.toString().trim(),
                        java.util.Map.of("lineStart", start, "lineEnd", i)));
                paragraph.setLength(0);
                start = i + 2;
            } else if (!lines[i].isBlank()) {
                if (!paragraph.isEmpty()) paragraph.append('\n');
                paragraph.append(lines[i]);
            }
        }
        if (!paragraph.isEmpty()) {
            blocks.add(new SourceBlock("lines-" + start + "-" + lines.length, "paragraph", paragraph.toString().trim(),
                    java.util.Map.of("lineStart", start, "lineEnd", lines.length)));
        }
        return blocks;
    }

    private List<SourceBlock> extractMarkdownBlocks(String text) {
        String normalized = text == null ? "" : text.replace("\u0000", "").trim();
        if (normalized.isBlank()) return List.of();
        String[] lines = normalized.split("\\R", -1);
        List<SourceBlock> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int paragraphStart = 1;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            int lineNumber = index + 1;
            if (line.isBlank()) {
                flushMarkdownParagraph(blocks, paragraph, paragraphStart, lineNumber - 1);
                paragraphStart = lineNumber + 1;
                continue;
            }
            if (line.startsWith("#")) {
                flushMarkdownParagraph(blocks, paragraph, paragraphStart, lineNumber - 1);
                String title = line.replaceFirst("^#+\\s*", "").trim();
                if (!title.isBlank()) {
                    blocks.add(new SourceBlock("title-" + lineNumber, "title", title,
                            java.util.Map.of("lineStart", lineNumber, "lineEnd", lineNumber)));
                }
                paragraphStart = lineNumber + 1;
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flushMarkdownParagraph(blocks, paragraph, paragraphStart, lineNumber - 1);
                List<String> items = new ArrayList<>();
                int end = lineNumber;
                int cursor = index;
                while (cursor < lines.length) {
                    String item = lines[cursor].trim();
                    if (!(item.startsWith("- ") || item.startsWith("* "))) break;
                    items.add(item.substring(2).trim());
                    end = cursor + 1;
                    cursor++;
                }
                blocks.add(new SourceBlock("list-" + lineNumber + "-" + end, "list",
                        String.join("\n", items), java.util.Map.of("lineStart", lineNumber, "lineEnd", end)));
                index = cursor - 1;
                paragraphStart = end + 1;
            } else {
                if (paragraph.isEmpty()) paragraphStart = lineNumber;
                if (!paragraph.isEmpty()) paragraph.append('\n');
                paragraph.append(line);
            }
        }
        flushMarkdownParagraph(blocks, paragraph, paragraphStart, lines.length);
        return blocks;
    }

    private void flushMarkdownParagraph(List<SourceBlock> blocks, StringBuilder paragraph, int start, int end) {
        if (paragraph.isEmpty()) return;
        blocks.add(new SourceBlock("paragraph-" + start + "-" + end, "paragraph", paragraph.toString().trim(),
                java.util.Map.of("lineStart", start, "lineEnd", end)));
        paragraph.setLength(0);
    }

    private String sha256(byte[] bytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
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

    public record ExtractedDocument(String sourceHash, String parserVersion, List<SourceBlock> blocks, List<String> warnings) {
    }

    public record SourceBlock(String blockId, String type, String text, java.util.Map<String, Object> locator) {
    }
}
