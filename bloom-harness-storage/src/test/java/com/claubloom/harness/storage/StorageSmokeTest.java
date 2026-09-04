package com.claubloom.harness.storage;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.storage.initializer.SqliteDatabaseInitializer;
import com.claubloom.harness.storage.mapper.SessionEntryMapper;
import com.claubloom.harness.storage.mapper.SessionMapper;
import com.claubloom.harness.storage.mapper.SessionStatsMapper;
import com.claubloom.harness.storage.service.SessionStorageService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 Smoke Tests for SQLite Session Storage (TC-P4-01).
 * Directly mirrors pi's 001_initial.sql and SQLite repo test suites.
 */
public class StorageSmokeTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource dataSource;
    private SqlSession sqlSession;
    private SessionStorageService storageService;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test-sessions.db");
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

        // Initialize schema
        SqliteDatabaseInitializer initializer = new SqliteDatabaseInitializer(dataSource);
        initializer.initialize();

        // Setup MyBatis-Plus environment
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        configuration.addMapper(SessionMapper.class);
        configuration.addMapper(SessionEntryMapper.class);
        configuration.addMapper(SessionStatsMapper.class);

        SqlSessionFactory sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        sqlSession = sqlSessionFactory.openSession(true);

        SessionMapper sessionMapper = sqlSession.getMapper(SessionMapper.class);
        SessionEntryMapper sessionEntryMapper = sqlSession.getMapper(SessionEntryMapper.class);
        SessionStatsMapper sessionStatsMapper = sqlSession.getMapper(SessionStatsMapper.class);

        storageService = new SessionStorageService(sessionMapper, sessionEntryMapper, sessionStatsMapper);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    /**
     * TC-P4-01: Session Lifecycle, Transcript Serialization, and Snapshot Restoration in SQLite.
     */
    @Test
    @DisplayName("TC-P4-01: Should create session, append transcript messages, and restore snapshot accurately")
    void should_persistSessionAndRestoreTranscript() {
        String sessionId = "sess-alpha-001";
        String cwd = tempDir.toAbsolutePath().toString();

        // 1. Create session
        storageService.createSession(sessionId, cwd, null, "{\"model\":\"gpt-4o\"}");
        assertThat(storageService.listSessions()).hasSize(1);
        assertThat(storageService.listSessions().get(0).id()).isEqualTo(sessionId);

        // 2. Append User Message
        UserMessage userMsg = UserMessage.text("Implement quicksort algorithm in Java");
        storageService.appendEntry(sessionId, "user_message", userMsg);

        // 3. Append Assistant Message
        AssistantMessage assistantMsg = AssistantMessage.text("Here is the quicksort implementation...");
        storageService.appendEntry(sessionId, "assistant_message", assistantMsg);

        // 4. Retrieve Transcript
        List<AgentMessage> transcript = storageService.getTranscript(sessionId);
        assertThat(transcript).hasSize(2);
        assertThat(transcript.get(0)).isInstanceOf(UserMessage.class);
        assertThat(transcript.get(1)).isInstanceOf(AssistantMessage.class);

        UserMessage readUserMsg = (UserMessage) transcript.get(0);
        assertThat(readUserMsg.content().get(0)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) readUserMsg.content().get(0)).text()).isEqualTo("Implement quicksort algorithm in Java");

        // 5. Retrieve Snapshot
        SessionSnapshot snapshot = storageService.getSnapshot(sessionId).orElseThrow();
        assertThat(snapshot.id()).isEqualTo(sessionId);
        assertThat(snapshot.cwd()).isEqualTo(cwd);
        assertThat(snapshot.transcript()).hasSize(2);
        assertThat(snapshot.revision()).isEqualTo(2);
    }
}
