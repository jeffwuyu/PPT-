package com.pptagent.api.agent;

import com.pptagent.agent.progress.AgentProgressEvent;
import com.pptagent.agent.progress.AgentProgressPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

@Primary
@Component
public class AgentProgressStreamService implements AgentProgressPublisher {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emittersByJobId = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Object relayLock = new Object();
    private final AtomicLong lastRelayedSequence = new AtomicLong();

    public AgentProgressStreamService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        Long latest = jdbcTemplate.queryForObject("select coalesce(max(id), 0) from agent_progress_events", Long.class);
        this.lastRelayedSequence.set(latest == null ? 0L : latest);
    }

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
        long cursor;
        try {
            cursor = Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            throw new CursorExpiredException("Event cursor is invalid; a fresh snapshot is required");
        }
        List<AgentProgressEvent> events = jdbcTemplate.query("select id, job_id, presentation_id, checkpoint_id, event_type, event_data, occurred_at "
                        + "from agent_progress_events where job_id = ? and id > ? order by id limit 501",
                (rs, rowNum) -> mapEvent(rs), jobId, cursor);
        if (events.size() > 500) {
            throw new CursorExpiredException("Event cursor requires a fresh snapshot");
        }
        return events;
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

    public void sendCursorExpired(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("CURSOR_EXPIRED")
                    .data(Map.of("code", "CURSOR_EXPIRED", "message", message), MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    @Override
    @Transactional
    public void publish(AgentProgressEvent event) {
        Long sequence = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (var statement = connection.prepareStatement(
                    "insert into agent_progress_events (job_id, presentation_id, checkpoint_id, event_type, event_data, occurred_at) values (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, event.jobId());
                statement.setLong(2, event.presentationId());
                if (event.checkpointId() == null) statement.setNull(3, java.sql.Types.BIGINT);
                else statement.setLong(3, event.checkpointId());
                statement.setString(4, event.type());
                statement.setString(5, objectMapper.writeValueAsString(event.data()));
                statement.setTimestamp(6, Timestamp.from(event.timestamp()));
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : null;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not persist agent progress event", exception);
            }
        });
        if (sequence == null) throw new IllegalStateException("Database did not return an event cursor");
        AgentProgressEvent sequenced = new AgentProgressEvent(String.valueOf(sequence), sequence, event.type(),
                event.jobId(), event.presentationId(), event.checkpointId(), event.data(), event.timestamp());
        Runnable dispatch = () -> relay(sequenced);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    /**
     * Database-backed relay for events committed by another application
     * instance. Redis may be used as a wake-up optimization later, but the
     * event table remains the source of truth for recovery and replay.
     */
    @Scheduled(fixedDelayString = "${ppt.agent.events.relay-interval-ms:250}")
    public void relayPersistedEvents() {
        synchronized (relayLock) {
            List<AgentProgressEvent> events = jdbcTemplate.query(
                    "select id, job_id, presentation_id, checkpoint_id, event_type, event_data, occurred_at "
                            + "from agent_progress_events where id > ? order by id limit 200",
                    (rs, rowNum) -> mapEvent(rs), lastRelayedSequence.get());
            for (AgentProgressEvent event : events) {
                lastRelayedSequence.accumulateAndGet(event.sequence(), Math::max);
                dispatch(event);
            }
        }
    }

    private void relay(AgentProgressEvent event) {
        synchronized (relayLock) {
            if (event.sequence() <= lastRelayedSequence.get()) return;
            lastRelayedSequence.set(event.sequence());
            dispatch(event);
        }
    }

    private void dispatch(AgentProgressEvent sequenced) {
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

    private AgentProgressEvent mapEvent(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long checkpoint = rs.getLong("checkpoint_id");
        Long checkpointId = rs.wasNull() ? null : checkpoint;
        try {
            Map<String, Object> data = objectMapper.readValue(rs.getString("event_data"), new TypeReference<>() { });
            return new AgentProgressEvent(String.valueOf(id), id, rs.getString("event_type"),
                    rs.getLong("job_id"), rs.getLong("presentation_id"), checkpointId, data,
                    rs.getTimestamp("occurred_at").toInstant());
        } catch (IOException exception) {
            throw new SQLException("Stored agent progress event is invalid", exception);
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
