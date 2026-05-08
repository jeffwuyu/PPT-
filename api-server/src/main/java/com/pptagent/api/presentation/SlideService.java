package com.pptagent.api.presentation;

import com.pptagent.api.presentation.dto.PresentationRequests;
import com.pptagent.api.presentation.dto.PresentationResponse;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlideService {

    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;
    private final PresentationAccessService accessService;
    private final PresentationJsonService jsonService;
    private final PresentationOrderService orderService;
    private final PresentationResponseMapper responseMapper;

    public SlideService(
            SlideRepository slideRepository,
            SlideElementRepository slideElementRepository,
            PresentationAccessService accessService,
            PresentationJsonService jsonService,
            PresentationOrderService orderService,
            PresentationResponseMapper responseMapper
    ) {
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
        this.accessService = accessService;
        this.jsonService = jsonService;
        this.orderService = orderService;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public PresentationResponse createSlide(Long userId, Long presentationId, PresentationRequests.CreateSlideRequest request) {
        PresentationEntity presentation = accessService.loadPresentationForEdit(userId, presentationId);
        SlideEntity slide = new SlideEntity(
                presentation,
                orderService.requestedOrderOrAppend(request.pageOrder(), slideRepository.countByPresentationId(presentationId)),
                request.title(),
                jsonService.toJson(jsonService.defaultObject(request.background())),
                request.speakerNotes()
        );
        slideRepository.insert(slide);
        List<SlideEntity> slides = slideRepository.findByPresentationIdOrderByPageOrderAsc(presentationId);
        orderService.reorderSlideWithTarget(slides, slide, request.pageOrder());
        return responseMapper.buildPresentationResponse(presentation, false);
    }

    @Transactional
    public PresentationResponse updateSlide(Long userId, Long slideId, PresentationRequests.UpdateSlideRequest request) {
        SlideEntity slide = accessService.loadSlideForEdit(userId, slideId);
        slide.update(request.title(), jsonService.toJson(jsonService.defaultObject(request.background())), request.speakerNotes());
        slideRepository.updateById(slide);
        if (request.pageOrder() != null) {
            List<SlideEntity> slides = slideRepository.findByPresentationIdOrderByPageOrderAsc(slide.getPresentation().getId());
            orderService.reorderSlideWithTarget(slides, slide, request.pageOrder());
        }
        return responseMapper.buildPresentationResponse(slide.getPresentation(), false);
    }

    @Transactional
    public PresentationResponse deleteSlide(Long userId, Long slideId) {
        SlideEntity slide = accessService.loadSlideForEdit(userId, slideId);
        PresentationEntity presentation = slide.getPresentation();
        List<SlideEntity> remaining = slideRepository.findByPresentationIdOrderByPageOrderAsc(presentation.getId()).stream()
                .filter(item -> !item.getId().equals(slide.getId()))
                .toList();
        slideElementRepository.findBySlideIdOrderByPageOrderAsc(slide.getId()).forEach(element -> {
            element.markDeleted();
            slideElementRepository.updateById(element);
        });
        slide.markDeleted();
        slideRepository.updateById(slide);
        orderService.normalizeSlideOrders(remaining);
        return responseMapper.buildPresentationResponse(presentation, false);
    }
}
