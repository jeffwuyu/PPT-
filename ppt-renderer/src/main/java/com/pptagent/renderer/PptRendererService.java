package com.pptagent.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PptRendererService {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final Color DEFAULT_TEXT_COLOR = Color.decode("#111827");
    private static final Color DEFAULT_MUTED_COLOR = Color.decode("#4b5563");
    private static final Color DEFAULT_PLACEHOLDER_FILL = Color.decode("#e5e7eb");
    private static final Color DEFAULT_PLACEHOLDER_BORDER = Color.decode("#94a3b8");

    public PptRenderResult render(JsonNode presentation, PptImageResolver imageResolver) {
        try {
            List<String> warnings = new ArrayList<>();
            XMLSlideShow show = buildShow(presentation, imageResolver, warnings);
            byte[] pptxBytes = toBytes(show);
            List<PptRenderResult.RenderedSlide> renderedSlides = renderThumbnails(pptxBytes, presentation);
            return new PptRenderResult(pptxBytes, renderedSlides, List.copyOf(warnings));
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "PPTX render failed");
        }
    }

    private XMLSlideShow buildShow(JsonNode presentation, PptImageResolver imageResolver, List<String> warnings) {
        XMLSlideShow show = new XMLSlideShow();
        int width = positiveInt(presentation.path("pageWidth"), DEFAULT_WIDTH);
        int height = positiveInt(presentation.path("pageHeight"), DEFAULT_HEIGHT);
        show.setPageSize(new Dimension(width, height));

        for (JsonNode slideNode : presentation.path("slides")) {
            XSLFSlide slide = show.createSlide();
            applyBackground(slide, slideNode, width, height);
            List<JsonNode> elements = new ArrayList<>();
            slideNode.path("elements").forEach(elements::add);
            elements.stream()
                    .sorted(Comparator.comparingInt((JsonNode element) -> element.path("zIndex").asInt(0))
                            .thenComparingInt(element -> element.path("pageOrder").asInt(0)))
                    .forEach(element -> renderElement(show, slide, element, imageResolver, warnings));
        }
        return show;
    }

    private void applyBackground(XSLFSlide slide, JsonNode slideNode, int width, int height) {
        Color fill = color(slideNode.path("background").path("color").asText("#ffffff"), Color.WHITE);
        XSLFAutoShape background = slide.createAutoShape();
        background.setShapeType(ShapeType.RECT);
        background.setAnchor(new Rectangle2D.Double(0, 0, width, height));
        background.setFillColor(fill);
        background.setLineColor(fill);
    }

    private void renderElement(
            XMLSlideShow show,
            XSLFSlide slide,
            JsonNode element,
            PptImageResolver imageResolver,
            List<String> warnings
    ) {
        String type = element.path("type").asText("");
        switch (type) {
            case "TEXT" -> renderText(slide, element);
            case "IMAGE" -> renderImage(show, slide, element, imageResolver, warnings);
            case "SHAPE", "CHART_PLACEHOLDER" -> renderPlaceholder(slide, element, type);
            default -> warnings.add("Unsupported element type ignored: " + type);
        }
    }

    private void renderText(XSLFSlide slide, JsonNode element) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(anchor(element));
        textBox.setRotation(element.path("rotation").asDouble(0));
        XSLFTextParagraph paragraph = textBox.addNewTextParagraph();
        paragraph.setLineSpacing(110.0);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(element.path("content").path("text").asText(""));
        JsonNode style = element.path("style");
        run.setFontSize(style.path("fontSize").asDouble(20));
        run.setFontColor(color(style.path("color").asText(""), DEFAULT_TEXT_COLOR));
        String weight = style.path("fontWeight").asText("");
        run.setBold("700".equals(weight) || "bold".equalsIgnoreCase(weight));
    }

    private void renderImage(
            XMLSlideShow show,
            XSLFSlide slide,
            JsonNode element,
            PptImageResolver imageResolver,
            List<String> warnings
    ) {
        JsonNode content = element.path("content");
        Long fileAssetId = content.hasNonNull("fileAssetId") ? content.path("fileAssetId").asLong() : null;
        if (fileAssetId != null) {
            Optional<PptImageData> image = imageResolver.resolve(fileAssetId);
            if (image.isPresent()) {
                try {
                    XSLFPictureData pictureData = show.addPicture(image.get().content(), pictureType(image.get().contentType()));
                    XSLFPictureShape picture = slide.createPicture(pictureData);
                    picture.setAnchor(anchor(element));
                    picture.setRotation(element.path("rotation").asDouble(0));
                    return;
                } catch (RuntimeException exception) {
                    warnings.add("Image asset " + fileAssetId + " could not be embedded");
                }
            } else {
                warnings.add("Image asset " + fileAssetId + " bytes were unavailable");
            }
        }
        renderImagePlaceholder(slide, element, content.path("altText").asText("Image placeholder"));
    }

    private void renderImagePlaceholder(XSLFSlide slide, JsonNode element, String label) {
        XSLFAutoShape box = slide.createAutoShape();
        box.setShapeType(ShapeType.RECT);
        box.setAnchor(anchor(element));
        box.setFillColor(DEFAULT_PLACEHOLDER_FILL);
        box.setLineColor(DEFAULT_PLACEHOLDER_BORDER);

        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(anchor(element));
        XSLFTextParagraph paragraph = textBox.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(label == null || label.isBlank() ? "Image placeholder" : label);
        run.setFontColor(DEFAULT_MUTED_COLOR);
        run.setFontSize(16.0);
    }

    private void renderPlaceholder(XSLFSlide slide, JsonNode element, String label) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.RECT);
        shape.setAnchor(anchor(element));
        shape.setFillColor(Color.decode("#f8fafc"));
        shape.setLineColor(DEFAULT_PLACEHOLDER_BORDER);

        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(anchor(element));
        XSLFTextRun run = textBox.addNewTextParagraph().addNewTextRun();
        run.setText(label);
        run.setFontColor(DEFAULT_MUTED_COLOR);
        run.setFontSize(16.0);
    }

    private byte[] toBytes(XMLSlideShow show) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            show.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private List<PptRenderResult.RenderedSlide> renderThumbnails(byte[] pptxBytes, JsonNode presentation) throws Exception {
        try (XMLSlideShow renderedShow = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            Dimension pageSize = renderedShow.getPageSize();
            List<JsonNode> slideNodes = new ArrayList<>();
            presentation.path("slides").forEach(slideNodes::add);
            List<PptRenderResult.RenderedSlide> slides = new ArrayList<>();
            for (int index = 0; index < renderedShow.getSlides().size(); index++) {
                XSLFSlide slide = renderedShow.getSlides().get(index);
                BufferedImage image = new BufferedImage(pageSize.width, pageSize.height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setPaint(Color.WHITE);
                graphics.fill(new Rectangle2D.Double(0, 0, pageSize.width, pageSize.height));
                slide.draw(graphics);
                graphics.dispose();

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", outputStream);
                    JsonNode slideNode = index < slideNodes.size() ? slideNodes.get(index) : null;
                    slides.add(new PptRenderResult.RenderedSlide(
                            slideNode == null ? index + 1 : slideNode.path("pageOrder").asInt(index + 1),
                            slideNode == null ? "" : slideNode.path("title").asText(""),
                            outputStream.toByteArray()
                    ));
                }
            }
            return List.copyOf(slides);
        }
    }

    private Rectangle2D.Double anchor(JsonNode element) {
        return new Rectangle2D.Double(
                element.path("x").asDouble(0),
                element.path("y").asDouble(0),
                Math.max(1, element.path("width").asDouble(1)),
                Math.max(1, element.path("height").asDouble(1))
        );
    }

    private PictureData.PictureType pictureType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return PictureData.PictureType.PNG;
        }
        return PictureData.PictureType.JPEG;
    }

    private int positiveInt(JsonNode value, int fallback) {
        return value.canConvertToInt() && value.asInt() > 0 ? value.asInt() : fallback;
    }

    private Color color(String value, Color fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Color.decode(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
