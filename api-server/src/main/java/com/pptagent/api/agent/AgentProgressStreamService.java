package com.pptagent.api.agent;

import com.pptagent.agent.progress.AgentProgressEvent;
import com.pptagent.agent.progress.AgentProgressPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

@Primary
@Component
public class AgentProgressStreamService implements AgentProgressPublisher {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emittersByJobId = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> sequenceByJobId = new ConcurrentHashMap<>();
    private final Map<Long, Deque<AgentProgressEvent>> historyByJobId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long jobId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByJobId.computeIfAbsent(jobId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(ignored -> remove(jobId, emitter));
        return emitter;
    }

    public List<AgentProgressEvent> replayAfter(Long jobId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) return List.of();
        Deque<AgentProgressEvent> history = historyByJobId.get(jobId);
        if (history == null) return List.of();
        synchronized (history) {
            boolean found = false;
            List<AgentProgressEvent> result = new ArrayList<>();
            for (AgentProgressEvent event : history) {
                if (found) result.add(event);
                if (event.eventId().equals(lastEventId)) found = true;
            }
            return found ? result : List.of();
        }
    }

    public void sendReplay(SseEmitter emitter, AgentProgressEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.eventId());
        data.put("sequence", event.sequence());
        data.put("type", event.type());
        data.put("jobId", event.jobId());
        data.put("presentationId", event.presentationId());
        data.put("checkpointId", event.checkpointId());
        data.put("timestamp", event.timestamp());
        data.put("data", event.data());
        try {
            emitter.send(SseEmitter.event().id(event.eventId()).name(event.type()).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    public void sendSnapshot(SseEmitter emitter, Object snapshot) {
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    @Override
    public void publish(AgentProgressEvent event) {
        long sequence = sequenceByJobId.computeIfAbsent(event.jobId(), ignored -> new AtomicLong()).incrementAndGet();
        AgentProgressEvent sequenced = new AgentProgressEvent(event.eventId(), sequence, event.type(), event.jobId(), event.presentationId(), event.checkpointId(), event.data(), event.timestamp());
        Deque<AgentProgressEvent> history = historyByJobId.computeIfAbsent(event.jobId(), ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(sequenced);
            while (history.size() > 500) history.removeFirst();
        }
        Set<SseEmitter> emitters = emittersByJobId.get(sequenced.jobId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", sequenced.eventId());
        data.put("sequence", sequenced.sequence());
        data.put("type", sequenced.type());
        data.put("jobId", sequenced.jobId());
        data.put("presentationId", sequenced.presentationId());
        data.put("checkpointId", sequenced.checkpointId());
        data.put("timestamp", sequenced.timestamp());
        data.put("data", sequenced.data());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(sequenced.eventId())
                        .name(sequenced.type())
                        .data(data, MediaType.APPLICATION_JSON));
                if ("done".equals(sequenced.type()) || "error".equals(sequenced.type())) {
                    emitter.complete();
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(sequenced.jobId(), emitter);
            }
        }
    }

    private void remove(Long jobId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByJobId.get(jobId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByJobId.remove(jobId);
        }
    }
}
