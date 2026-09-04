package com.claubloom.harness.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.session.SessionMetadata;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.protocol.session.Usage;
import com.claubloom.harness.storage.entity.SessionEntity;
import com.claubloom.harness.storage.entity.SessionEntryEntity;
import com.claubloom.harness.storage.entity.SessionStatsEntity;
import com.claubloom.harness.storage.mapper.SessionEntryMapper;
import com.claubloom.harness.storage.mapper.SessionMapper;
import com.claubloom.harness.storage.mapper.SessionStatsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Session storage service for SQLite persistence.
 * Directly mirrors pi's repo.ts (sessions, entries, session_stats).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStorageService {

    private final SessionMapper sessionMapper;
    private final SessionEntryMapper sessionEntryMapper;
    private final SessionStatsMapper sessionStatsMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SessionEntity createSession(String sessionId, String cwd, String parentSessionId, String metadata) {
        String id = sessionId != null && !sessionId.isBlank() ? sessionId : UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        SessionEntity session = SessionEntity.builder()
                .id(id)
                .createdAt(now)
                .cwd(cwd != null ? cwd : System.getProperty("user.dir"))
                .parentSessionId(parentSessionId)
                .metadata(metadata)
                .build();
        sessionMapper.insert(session);

        SessionStatsEntity stats = SessionStatsEntity.builder()
                .sessionId(id)
                .messageCount(0)
                .cachedTokens(0.0)
                .uncachedTokens(0.0)
                .totalTokens(0.0)
                .costTotal(0.0)
                .build();
        sessionStatsMapper.insert(stats);

        log.info("Created session {} in {}", id, session.getCwd());
        return session;
    }

    @Transactional
    public boolean updateSessionCwd(String sessionId, String newCwd) {
        if (sessionId == null || newCwd == null || newCwd.isBlank()) return false;
        SessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity != null) {
            entity.setCwd(newCwd);
            sessionMapper.updateById(entity);
            log.info("Updated session {} cwd to {}", sessionId, newCwd);
            return true;
        }
        return false;
    }

    @Transactional
    public void appendEntry(String sessionId, String type, AgentMessage message) {
        if (sessionId == null || message == null) return;

        try {
            int nextSeq = sessionEntryMapper.getNextSeq(sessionId);
            String payloadJson = objectMapper.writeValueAsString(message);
            long now = System.currentTimeMillis();

            SessionEntryEntity entry = SessionEntryEntity.builder()
                    .sessionId(sessionId)
                    .seq(nextSeq)
                    .id(message.id() != null ? message.id() : UUID.randomUUID().toString())
                    .parentId(null)
                    .type(type != null ? type : message.role())
                    .timestamp(now)
                    .payload(payloadJson)
                    .build();

            sessionEntryMapper.insert(entry);

            // Update stats
            SessionStatsEntity stats = sessionStatsMapper.selectById(sessionId);
            if (stats != null) {
                stats.setMessageCount(stats.getMessageCount() + 1);
                sessionStatsMapper.updateById(stats);
            }
        } catch (Exception e) {
            log.error("Failed appending entry for session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed appending entry", e);
        }
    }

    public List<AgentMessage> getTranscript(String sessionId) {
        if (sessionId == null) return List.of();

        List<SessionEntryEntity> entries = sessionEntryMapper.findBySessionIdOrdered(sessionId);
        List<AgentMessage> messages = new ArrayList<>();

        for (SessionEntryEntity entry : entries) {
            try {
                AgentMessage msg = objectMapper.readValue(entry.getPayload(), AgentMessage.class);
                messages.add(msg);
            } catch (Exception e) {
                log.warn("Failed deserializing message payload for entry {}: {}", entry.getId(), e.getMessage());
            }
        }
        return messages;
    }

    public Optional<SessionSnapshot> getSnapshot(String sessionId) {
        if (sessionId == null) return Optional.empty();

        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) return Optional.empty();

        List<AgentMessage> messages = getTranscript(sessionId);
        SessionStatsEntity stats = sessionStatsMapper.selectById(sessionId);

        long updatedAt = session.getCreatedAt();
        if (!messages.isEmpty()) {
            updatedAt = messages.get(messages.size() - 1).timestamp();
        }

        SessionSnapshot snapshot = new SessionSnapshot(
                session.getId(),
                session.getId(),
                session.getCwd(),
                session.getCreatedAt(),
                updatedAt,
                SessionPhase.IDLE,
                null,
                ThinkingLevel.OFF,
                false,
                false,
                stats != null ? stats.getMessageCount() : messages.size(),
                messages,
                List.of(),
                0
        );

        return Optional.of(snapshot);
    }

    public List<SessionMetadata> listSessions() {
        List<SessionEntity> list = sessionMapper.selectList(new LambdaQueryWrapper<SessionEntity>()
                .orderByDesc(SessionEntity::getCreatedAt));

        return list.stream()
                .map(s -> new SessionMetadata(
                        s.getId(),
                        s.getCreatedAt(),
                        s.getCreatedAt(),
                        s.getParentSessionId(),
                        s.getId(),
                        s.getCwd()
                ))
                .toList();
    }
}
