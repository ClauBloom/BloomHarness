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
 * 阶段 4 SQLite 会话存储冒烟测试（TC-P4-01）。
 * 验证会话生命周期、对话记录序列化与快照恢复的正确性。
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

        // 初始化表结构
        SqliteDatabaseInitializer initializer = new SqliteDatabaseInitializer(dataSource);
        initializer.initialize();

        // 搭建 MyBatis-Plus 环境
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
     * TC-P4-01:在 SQLite 中验证会话生命周期、对话记录序列化与快照恢复。
     */
    @Test
    @DisplayName("TC-P4-01: Should create session, append transcript messages, and restore snapshot accurately")
    void should_persistSessionAndRestoreTranscript() {
        String sessionId = "sess-alpha-001";
        String cwd = tempDir.toAbsolutePath().toString();

        // 1. 创建会话
        storageService.createSession(sessionId, cwd, null, "{\"model\":\"gpt-4o\"}");
        assertThat(storageService.listSessions()).hasSize(1);
        assertThat(storageService.listSessions().get(0).id()).isEqualTo(sessionId);

        // 2. 追加用户消息
        UserMessage userMsg = UserMessage.text("Implement quicksort algorithm in Java");
        storageService.appendEntry(sessionId, "user_message", userMsg);

        // 3. 追加助手消息
        AssistantMessage assistantMsg = AssistantMessage.text("Here is the quicksort implementation...");
        storageService.appendEntry(sessionId, "assistant_message", assistantMsg);

        // 4. 获取对话记录
        List<AgentMessage> transcript = storageService.getTranscript(sessionId);
        assertThat(transcript).hasSize(2);
        assertThat(transcript.get(0)).isInstanceOf(UserMessage.class);
        assertThat(transcript.get(1)).isInstanceOf(AssistantMessage.class);

        UserMessage readUserMsg = (UserMessage) transcript.get(0);
        assertThat(readUserMsg.content().get(0)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) readUserMsg.content().get(0)).text()).isEqualTo("Implement quicksort algorithm in Java");

        // 5. 获取快照
        SessionSnapshot snapshot = storageService.getSnapshot(sessionId).orElseThrow();
        assertThat(snapshot.id()).isEqualTo(sessionId);
        assertThat(snapshot.cwd()).isEqualTo(cwd);
        assertThat(snapshot.transcript()).hasSize(2);
        assertThat(snapshot.revision()).isEqualTo(2);
    }
}
