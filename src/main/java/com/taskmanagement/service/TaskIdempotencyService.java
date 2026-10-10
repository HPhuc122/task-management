package com.taskmanagement.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.taskmanagement.dto.TaskRequest;
import com.taskmanagement.dto.TaskResponse;
import com.taskmanagement.exception.IdempotencyConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskIdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TaskIdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TaskResponse execute(Long actorId, String key, TaskRequest request,
                                Supplier<TaskResponse> create, Consumer<Long> authorizeReplay) {
        if (!key.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
        String hash = fingerprint(request);
        // A conflicting INSERT waits for the first transaction to commit or roll back.
        int inserted = jdbc.update("""
                INSERT INTO api_idempotency(actor_id, operation, request_key, request_hash)
                VALUES (?, 'CREATE_TASK', ?, ?) ON CONFLICT DO NOTHING
                """, actorId, key, hash);
        if (inserted == 0) {
            return jdbc.queryForObject("""
                    SELECT request_hash, response_json FROM api_idempotency
                    WHERE actor_id = ? AND operation = 'CREATE_TASK' AND request_key = ? FOR UPDATE
                    """, (rs, row) -> {
                if (!hash.equals(rs.getString("request_hash"))) throw new IdempotencyConflictException();
                TaskResponse response = readResponse(rs.getString("response_json"));
                authorizeReplay.accept(response.id());
                return response;
            }, actorId, key);
        }
        TaskResponse response = create.get();
        jdbc.update("""
                UPDATE api_idempotency SET response_json = ?
                WHERE actor_id = ? AND operation = 'CREATE_TASK' AND request_key = ?
                """, json(response), actorId, key);
        return response;
    }

    private String fingerprint(TaskRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize idempotency data", exception);
        }
    }

    private TaskResponse readResponse(String json) {
        try {
            return mapper.readValue(json, TaskResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot read idempotency response", exception);
        }
    }
}
