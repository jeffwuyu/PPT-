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

@Service
public class ElementService {

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
        slideElementRepository.updateById(element);
        if (request.pageOrder() != null) {
            orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(element.getSlide().getId()), element, request.pageOrder());
        }
        return responseMapper.toElementResponse(element, true);
    }

    @Transactional
    public void deleteElement(Long userId, Long elementId) {
        SlideElementEntity element = accessService.loadElementForEdit(userId, elementId);
        List<SlideElementEntity> remaining = slideElementRepository.findBySlideIdOrderByPageOrderAsc(element.getSlide().getId()).stream()
                .filter(item -> !item.getId().equals(element.getId()))
                .toList();
        slideElementRepository.deleteById(element.getId());
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
        slideElementRepository.updateById(element);
        if (operation.pageOrder() != null) {
            orderService.reorderElementWithTarget(slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()), element, operation.pageOrder());
        }
    }

    private void deleteElementFromOperation(SlideEntity slide, PresentationRequests.ElementOperation operation) {
        SlideElementEntity element = requireBatchElement(slide, operation.elementId());
        slideElementRepository.deleteById(element.getId());
    }

    private void reorderElementFromOperation(SlideEntity slide, PresentationRequests.ElementOperation operation) {
        SlideElementEntity element = requireBatchElement(slide, operation.elementId());
        if (operation.pageOrder() == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "pageOrder is required for reorder");
        }
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
}
