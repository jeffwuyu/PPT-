package com.pptagent.domain.file;

import java.util.Set;

public enum FileType {
    SOURCE_TEXT(
            Set.of(
                    "text/plain",
                    "text/markdown",
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ),
            Set.of("txt", "md", "pdf", "docx")
    ),
    USER_IMAGE(Set.of("image/png", "image/jpeg", "image/webp"), Set.of("png", "jpg", "jpeg", "webp")),
    CRAWLED_IMAGE(Set.of("image/png", "image/jpeg", "image/webp"), Set.of("png", "jpg", "jpeg", "webp")),
    GENERATED_PPTX(Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"), Set.of("pptx")),
    COVER_IMAGE(Set.of("image/png", "image/jpeg", "image/webp"), Set.of("png", "jpg", "jpeg", "webp")),
    SLIDE_THUMBNAIL(Set.of("image/png", "image/jpeg", "image/webp"), Set.of("png", "jpg", "jpeg", "webp"));

    private final Set<String> contentTypes;
    private final Set<String> extensions;

    FileType(Set<String> contentTypes, Set<String> extensions) {
        this.contentTypes = contentTypes;
        this.extensions = extensions;
    }

    public boolean supports(String contentType, String extension) {
        return contentTypes.contains(contentType.toLowerCase()) && extensions.contains(extension.toLowerCase());
    }
}
