package com.claubloom.harness.storage.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Executes SQLite table migrations on startup, directly matching pi's 001_initial.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqliteDatabaseInitializer {

    private final DataSource dataSource;

    public void initialize() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Create sessions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL,
                    cwd TEXT NOT NULL,
                    parent_session_id TEXT NULL,
                    metadata TEXT NULL
                )
            """);

            // 2. Create entries table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entries (
                    session_id TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    id TEXT NOT NULL,
                    parent_id TEXT NULL,
                    type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    payload TEXT NOT NULL,
                    PRIMARY KEY (session_id, id),
                    UNIQUE (session_id, seq)
                )
            """);

            // 3. Create session_stats table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_stats (
                    session_id TEXT PRIMARY KEY,
                    message_count INTEGER NOT NULL,
                    cached_tokens REAL NOT NULL,
                    uncached_tokens REAL NOT NULL,
                    total_tokens REAL NOT NULL,
                    cost_total REAL NOT NULL
                )
            """);

            log.info("Initialized SQLite database tables (sessions, entries, session_stats)");
        } catch (Exception e) {
            log.error("Failed to initialize SQLite tables: {}", e.getMessage(), e);
            throw new RuntimeException("SQLite initialization failed", e);
        }
    }
}
