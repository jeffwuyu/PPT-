package com.pptagent.api.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class GenerationConcurrencyService {
    private final JdbcTemplate jdbc;

    public GenerationConcurrencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public String tryAcquire(int limit, long ttlSeconds) {
        jdbc.queryForObject("select id from agent_generation_model_slot_mutex where id=1 for update", Integer.class);
        jdbc.update("delete from agent_generation_model_slots where expires_at <= CURRENT_TIMESTAMP");
        int active = jdbc.queryForObject("select count(*) from agent_generation_model_slots", Integer.class);
        if (active >= limit) return null;
        String token = UUID.randomUUID().toString();
        jdbc.update("insert into agent_generation_model_slots(slot_token,expires_at) values(?,?)", token,
                java.sql.Timestamp.from(Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    @Transactional
    public void release(String token) {
        if (token != null) jdbc.update("delete from agent_generation_model_slots where slot_token=?", token);
    }
}
