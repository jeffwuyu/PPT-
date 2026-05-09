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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Primary
@Component
public class AgentProgressStreamService implements AgentProgressPublisher {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emittersByJobId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long jobId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByJobId.computeIfAbsent(jobId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(ignored -> remove(jobId, emitter));
        return emitter;
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
        Set<SseEmitter> emitters = emittersByJobId.get(event.jobId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.type());
        data.put("jobId", event.jobId());
        data.put("presentationId", event.presentationId());
        data.put("checkpointId", event.checkpointId());
        data.put("timestamp", event.timestamp());
        data.put("data", event.data());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(data, MediaType.APPLICATION_JSON));
                if ("done".equals(event.type()) || "error".equals(event.type())) {
                    emitter.complete();
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(event.jobId(), emitter);
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
