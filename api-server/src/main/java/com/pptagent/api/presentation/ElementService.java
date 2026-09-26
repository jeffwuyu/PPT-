package com.pptagent.api.presentation;

import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ElementService {
    private static final Set<String> AI_COMMANDS = Set.of("POLISH_TEXT", "ADD_SLIDE", "DELETE_SLIDE", "CHANGE_THEME", "RELAYOUT");
    private final Map<String, List<PresentationResponse.ElementResponse>> aiMutationResults = new ConcurrentHashMap<>();

    private final SlideElementRepository slideElementRepository;
    private final SlideRepository slideRepository;
    private final PresentationAccessService accessService;
    private final PresentationJsonService jsonService;
    private final PresentationOrderService orderService;
    private final ElementValidationService elementValidationService;
    private final PresentationResponseMapper responseMapper;

    public ElementService(
            SlideElementRepository slideElementRepository,
            SlideRepository slideRepository,
            PresentationAccessService accessService,
            PresentationJsonService jsonService,
            PresentationOrderService orderService,
            ElementValidationService elementValidationService,
            PresentationResponseMapper responseMapper
    ) {
        this.slideElementRepository = slideElementRepository;
        this.slideRepository = slideRepository;
        this.accessService = accessService;
        this.jsonService = jsonService;
        this.orderService = orderService;
        this.elementValidationService = elementValidationService;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public PresentationResponse.ElementResponse createElement(Long userId, Long slideId, PresentationRequests.CreateElementRequest request) {
        SlideEntity slide = accessService.loadSlideForEdit(userId, slideId);
        elementValidationService.validateElement(userId, request.type(), request.width(), request.height(), request.content());
        SlideElementEntity element = new SlideElementEntity(
                slide,
                UUID.randomUUID().toString(),
                request.type(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                jsonService.defaultDouble(request.rotation()),
                jsonService.defaultInt(request.zIndex()),
                orderService.requestedOrderOrAppend(request.pageOrder(), slideElementRepository.countBySlideId(slideId)),
                Boolean.TRUE.equals(request.initiallyPresent()),
                jsonService.toJson(jsonService.defaultObject(request.content())),
                jsonService.toJson(jsonService.defaultObject(request.style()))
        );
        slideElementRepository.insert(element);
        orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slideId), element, request.pageOrder());
        return responseMapper.toElementResponse(element, true);
    }

    @Transactional
    public PresentationResponse.ElementResponse updateElement(Long userId, Long elementId, PresentationRequests.UpdateElementRequest request) {
        SlideElementEntity element = accessService.loadElementForEdit(userId, elementId);
        requireEditableRevision(element, request.expectedRevision());
        elementValidationService.validateElement(userId, request.type(), request.width(), request.height(), request.content());
        element.update(
                request.type(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                jsonService.defaultDouble(request.rotation()),
                jsonService.defaultInt(request.zIndex()),
                request.pageOrder() == null ? element.getPageOrder() : orderService.requestedOrderOrAppend(request.pageOrder(), slideElementRepository.countBySlideId(element.getSlide().getId())),
                request.initiallyPresent() == null ? element.isInitiallyPresent() : request.initiallyPresent(),
                jsonService.toJson(jsonService.defaultObject(request.content())),
                jsonService.toJson(jsonService.defaultObject(request.style()))
        );
        if (slideElementRepository.updateRevisionIfCurrent(elementId, request.expectedRevision(), element) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before editing");
        }
        if (request.pageOrder() != null) {
            orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(element.getSlide().getId()), element, request.pageOrder());
        }
        return responseMapper.toElementResponse(element, true);
    }

    @Transactional
    public void deleteElement(Long userId, Long elementId, int expectedRevision) {
        SlideElementEntity element = accessService.loadElementForEdit(userId, elementId);
        requireEditableRevision(element, expectedRevision);
        List<SlideElementEntity> remaining = slideElementRepository.findBySlideIdOrderByPageOrderAsc(element.getSlide().getId()).stream()
                .filter(item -> !item.getId().equals(element.getId()))
                .toList();
        element.markDeleted();
        if (slideElementRepository.updateRevisionIfCurrent(elementId, expectedRevision, element) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before deleting");
        }
        orderService.normalizeElementOrders(remaining);
    }

    @Transactional
    public PresentationResponse.SlideResponse batchElements(Long userId, Long slideId, PresentationRequests.BatchElementRequest request) {
        SlideEntity slide = accessService.loadSlideForEdit(userId, slideId);
        if (request.operations().isEmpty()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Batch operations must not be empty");
        }
        for (PresentationRequests.ElementOperation operation : request.operations()) {
            String action = operation.action().toUpperCase(Locale.ROOT);
            switch (action) {
                case "ADD" -> addElementFromOperation(userId, slide, operation);
                case "UPDATE" -> updateElementFromOperation(userId, slide, operation);
                case "DELETE" -> deleteElementFromOperation(slide, operation);
                case "REORDER" -> reorderElementFromOperation(slide, operation);
                default -> throw new PlatformException(ErrorCode.BAD_REQUEST, "Unknown element batch action");
            }
        }
        orderService.normalizeElementOrders(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slideId));
        return responseMapper.toSlideResponse(slide, true);
    }

    @Transactional
    public List<PresentationResponse.ElementResponse> applyAiEdits(Long userId, Long presentationId, List<PresentationRequests.AiElementEdit> edits) {
        return applyAiEdits(userId, presentationId,
                new PresentationRequests.AiEditRequest(edits));
    }

    @Transactional
    public List<PresentationResponse.ElementResponse> applyAiEdits(Long userId, Long presentationId,
                                                                    PresentationRequests.AiEditRequest request) {
        String command = request.command() == null || request.command().isBlank()
                ? "POLISH_TEXT" : request.command().toUpperCase(Locale.ROOT);
        if (!AI_COMMANDS.contains(command)) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Unsupported AI edit command");
        }
        String mutationId = request.clientMutationId() == null || request.clientMutationId().isBlank()
                ? request.idempotencyKey() : request.clientMutationId();
        if (mutationId != null && !mutationId.isBlank()) {
            String cacheKey = userId + ":" + presentationId + ":" + mutationId;
            List<PresentationResponse.ElementResponse> previous = aiMutationResults.get(cacheKey);
            if (previous != null) return previous;
        }
        List<PresentationRequests.AiElementEdit> edits = request.edits();
        if (edits == null || edits.isEmpty()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "AI edit command must contain at least one element");
        }
        List<SlideElementEntity> targets = edits.stream().map(edit -> accessService.loadElementForEdit(userId, edit.elementId())).toList();
        if (targets.stream().anyMatch(element -> !element.getSlide().getPresentationId().equals(presentationId))) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "AI edit target does not belong to the requested presentation");
        }
        for (int index = 0; index < edits.size(); index++) {
            PresentationRequests.AiElementEdit edit = edits.get(index);
            SlideElementEntity element = targets.get(index);
            if (element.isLocked() || !element.matchesRevision(edit.expectedRevision())) {
                throw new PlatformException(ErrorCode.CONFLICT, "AI edit target is locked or has a stale revision");
            }
        }
        for (int index = 0; index < edits.size(); index++) {
            PresentationRequests.AiElementEdit edit = edits.get(index);
            SlideElementEntity element = targets.get(index);
            String content = edit.content() == null ? null : jsonService.toJson(edit.content());
            String style = edit.style() == null ? null : jsonService.toJson(edit.style());
            element.updateFromAi(content, style, edit.x(), edit.y(), edit.width(), edit.height());
            if (slideElementRepository.updateRevisionIfCurrent(element.getId(), edit.expectedRevision(), element) != 1) {
                throw new PlatformException(ErrorCode.CONFLICT, "AI edit target changed during commit");
            }
        }
        List<PresentationResponse.ElementResponse> result = targets.stream()
                .map(element -> responseMapper.toElementResponse(element, true)).toList();
        if (mutationId != null && !mutationId.isBlank()) {
            aiMutationResults.put(userId + ":" + presentationId + ":" + mutationId, result);
        }
        return result;
    }

    @Transactional
    public PresentationResponse.ElementResponse setLock(Long userId, Long elementId, PresentationRequests.ElementLockRequest request) {
        SlideElementEntity element = accessService.loadElementForEdit(userId, elementId);
        if (!element.matchesRevision(request.expectedRevision())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before changing lock");
        }
        if (slideElementRepository.updateLockIfCurrent(elementId, request.expectedRevision(), request.locked()) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before changing lock");
        }
        element.setLocked(request.locked());
        element.incrementRevision();
        return responseMapper.toElementResponse(element, true);
    }

    private void addElementFromOperation(Long userId, SlideEntity slide, PresentationRequests.ElementOperation operation) {
        elementValidationService.validateElement(userId, operation.type(), operation.width(), operation.height(), operation.content());
        SlideElementEntity element = new SlideElementEntity(
                slide,
                UUID.randomUUID().toString(),
                operation.type(),
                jsonService.requiredDouble(operation.x(), "x"),
                jsonService.requiredDouble(operation.y(), "y"),
                operation.width(),
                operation.height(),
                jsonService.defaultDouble(operation.rotation()),
                jsonService.defaultInt(operation.zIndex()),
                orderService.requestedOrderOrAppend(operation.pageOrder(), slideElementRepository.countBySlideId(slide.getId())),
                Boolean.TRUE.equals(operation.initiallyPresent()),
                jsonService.toJson(jsonService.defaultObject(operation.content())),
                jsonService.toJson(jsonService.defaultObject(operation.style()))
        );
        slideElementRepository.insert(element);
        orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()), element, operation.pageOrder());
    }

    private void updateElementFromOperation(Long userId, SlideEntity slide, PresentationRequests.ElementOperation operation) {
        SlideElementEntity element = requireBatchElement(slide, operation.elementId());
        requireEditableRevision(element, operation.expectedRevision());
        elementValidationService.validateElement(userId, operation.type(), operation.width(), operation.height(), operation.content());
        element.update(
                operation.type(),
                jsonService.requiredDouble(operation.x(), "x"),
                jsonService.requiredDouble(operation.y(), "y"),
                operation.width(),
                operation.height(),
                jsonService.defaultDouble(operation.rotation()),
                jsonService.defaultInt(operation.zIndex()),
                operation.pageOrder() == null ? element.getPageOrder() : orderService.requestedOrderOrAppend(operation.pageOrder(), slideElementRepository.countBySlideId(slide.getId())),
                operation.initiallyPresent() == null ? element.isInitiallyPresent() : operation.initiallyPresent(),
                jsonService.toJson(jsonService.defaultObject(operation.content())),
                jsonService.toJson(jsonService.defaultObject(operation.style()))
        );
        if (slideElementRepository.updateRevisionIfCurrent(element.getId(), operation.expectedRevision(), element) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before editing");
        }
        if (operation.pageOrder() != null) {
            orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()), element, operation.pageOrder());
        }
    }

    private void deleteElementFromOperation(SlideEntity slide, PresentationRequests.ElementOperation operation) {
        SlideElementEntity element = requireBatchElement(slide, operation.elementId());
        requireEditableRevision(element, operation.expectedRevision());
        element.markDeleted();
        if (slideElementRepository.updateRevisionIfCurrent(element.getId(), operation.expectedRevision(), element) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before deleting");
        }
    }

    private void reorderElementFromOperation(SlideEntity slide, PresentationRequests.ElementOperation operation) {
        SlideElementEntity element = requireBatchElement(slide, operation.elementId());
        if (operation.pageOrder() == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "pageOrder is required for reorder");
        }
        requireEditableRevision(element, operation.expectedRevision());
        orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()), element, operation.pageOrder());
    }

    private SlideElementEntity requireBatchElement(SlideEntity slide, Long elementId) {
        if (elementId == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "elementId is required");
        }
        SlideElementEntity element = slideElementRepository.findWithSlideAndPresentationById(elementId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Element not found"));
        SlideEntity elementSlide = slideRepository.findWithPresentationById(element.getSlideId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Slide not found"));
        elementSlide.setPresentation(accessService.loadActivePresentation(elementSlide.getPresentationId()));
        element.setSlide(elementSlide);
        if (!element.getSlide().getId().equals(slide.getId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "Element does not belong to slide");
        }
        return element;
    }

    private void requireEditableRevision(SlideElementEntity element, Integer expectedRevision) {
        if (expectedRevision == null || !element.matchesRevision(expectedRevision)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Element revision changed; reload before editing");
        }
    }
}
