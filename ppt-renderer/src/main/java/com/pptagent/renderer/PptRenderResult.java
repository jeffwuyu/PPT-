package com.pptagent.renderer;

import java.util.List;

public record PptRenderResult(
        byte[] pptx,
        List<RenderedSlide> slides,
        List<String> warnings
) {
    public record RenderedSlide(
            int pageOrder,
            String title,
            byte[] thumbnailPng
    ) {
    }
}
