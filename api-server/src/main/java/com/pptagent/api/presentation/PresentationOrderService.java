package com.pptagent.api.presentation;

import com.pptagent.domain.presentation.SlideElementEntity;
import com.pptagent.domain.presentation.SlideEntity;
import com.pptagent.domain.presentation.repository.SlideElementRepository;
import com.pptagent.domain.presentation.repository.SlideRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PresentationOrderService {

    private final SlideRepository slideRepository;
    private final SlideElementRepository slideElementRepository;

    public PresentationOrderService(SlideRepository slideRepository, SlideElementRepository slideElementRepository) {
        this.slideRepository = slideRepository;
        this.slideElementRepository = slideElementRepository;
    }

    public void reorderSlideWithTarget(List<SlideEntity> slides, SlideEntity target, Integer requestedOrder) {
        List<SlideEntity> sorted = new ArrayList<>(slides);
        sorted.sort(Comparator.comparingInt(SlideEntity::getPageOrder).thenComparing(SlideEntity::getId));
        sorted.removeIf(slide -> slide.getId().equals(target.getId()));
        sorted.add(clampedIndex(requestedOrder, sorted.size()), target);
        normalizeSlideOrders(sorted);
    }

    public void normalizeSlideOrders(List<SlideEntity> slides) {
        for (int index = 0; index < slides.size(); index++) {
            SlideEntity slide = slides.get(index);
            slide.changePageOrder(index + 1);
            slideRepository.updateById(slide);
        }
    }

    public void reorderElementWithTarget(List<SlideElementEntity> elements, SlideElementEntity target, Integer requestedOrder) {
        List<SlideElementEntity> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator.comparingInt(SlideElementEntity::getPageOrder).thenComparing(SlideElementEntity::getId));
        sorted.removeIf(element -> element.getId().equals(target.getId()));
        sorted.add(clampedIndex(requestedOrder, sorted.size()), target);
        normalizeElementOrders(sorted);
    }

    public void normalizeElementOrders(List<SlideElementEntity> elements) {
        for (int index = 0; index < elements.size(); index++) {
            SlideElementEntity element = elements.get(index);
            element.changePageOrder(index + 1);
            slideElementRepository.updateById(element);
        }
    }

    public int requestedOrderOrAppend(Integer requestedOrder, long currentCount) {
        if (requestedOrder == null) {
            return Math.toIntExact(currentCount + 1);
        }
        return Math.max(1, requestedOrder);
    }

    private int clampedIndex(Integer requestedOrder, int currentSizeWithoutTarget) {
        if (requestedOrder == null) {
            return currentSizeWithoutTarget;
        }
        return Math.max(0, Math.min(requestedOrder - 1, currentSizeWithoutTarget));
    }
}
