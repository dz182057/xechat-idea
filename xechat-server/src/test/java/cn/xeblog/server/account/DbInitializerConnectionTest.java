package cn.xeblog.server.account;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.xeblog.server.config.GlobalConfig;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DbInitializerConnectionTest {

    @After
    public void tearDown() throws Exception {
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void initShouldNotInvalidatePooledConnectionWhenClosingSession() throws Exception {
        Path root = Files.createTempDirectory("xechat-db-init-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();

        Logger jdbcLogger = (Logger) LoggerFactory.getLogger("org.apache.ibatis.transaction.jdbc.JdbcTransaction");
        Logger poolLogger = (Logger) LoggerFactory.getLogger("org.apache.ibatis.datasource.pooled.PooledDataSource");
        Level oldJdbcLevel = jdbcLogger.getLevel();
        Level oldPoolLevel = poolLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        jdbcLogger.setLevel(Level.DEBUG);
        poolLogger.setLevel(Level.DEBUG);
        jdbcLogger.addAppender(appender);
        poolLogger.addAppender(appender);

        try {
            DbInitializer.initIfNeeded();
        } finally {
            jdbcLogger.detachAppender(appender);
            poolLogger.detachAppender(appender);
            jdbcLogger.setLevel(oldJdbcLevel);
            poolLogger.setLevel(oldPoolLevel);
        }

        boolean hasInvalidConnectionLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Connection is invalid")
                        || message.contains("attempted to return to the pool, discarding connection"));
        assertFalse(hasInvalidConnectionLog);
    }

    @Test
    public void initShouldMigrateLegacyQuickQuizRowsToTacitQuizAndClearQuickQuizTables() throws Exception {
        Path root = Files.createTempDirectory("xechat-db-migrate-quick-quiz-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        Files.createDirectories(Paths.get(GlobalConfig.DATA_DIR));
        resetFactory();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + GlobalConfig.DB_PATH);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE quick_quiz_questions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "question TEXT NOT NULL UNIQUE," +
                    "options_json TEXT NOT NULL," +
                    "sort_order INTEGER NOT NULL DEFAULT 0," +
                    "active INTEGER NOT NULL DEFAULT 1," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")");
            st.execute("CREATE TABLE quick_quiz_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "room_id TEXT NOT NULL," +
                    "question_id INTEGER NOT NULL," +
                    "player_key TEXT NOT NULL," +
                    "username TEXT NOT NULL," +
                    "choice_index INTEGER NOT NULL," +
                    "choice_text TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL" +
                    ")");
            st.execute("INSERT INTO quick_quiz_questions (" +
                    "id, question, options_json, sort_order, active, created_at, updated_at) " +
                    "VALUES (1, '旧默契题', '[\"A\",\"B\"]', 3, 1, 100, 200)");
            st.execute("INSERT INTO quick_quiz_records (" +
                    "id, room_id, question_id, player_key, username, choice_index, choice_text, created_at) " +
                    "VALUES (2, 'room-1', 1, 'alice', 'Alice', 0, 'A', 300)");
        }

        DbInitializer.initIfNeeded();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            Connection conn = session.getConnection();
            assertEquals(1, countRows(conn, "tacit_quiz_questions"));
            assertEquals(1, countRows(conn, "tacit_quiz_records"));
            assertEquals(0, countRows(conn, "quick_quiz_questions"));
            assertEquals(0, countRows(conn, "quick_quiz_records"));
            assertEquals("旧默契题", scalarText(conn,
                    "SELECT question FROM tacit_quiz_questions WHERE id = 1"));
            assertEquals(1, scalarLong(conn,
                    "SELECT COUNT(1) FROM db_migrations WHERE id = 'quick_quiz_to_tacit_quiz_20260618'"));
        }
    }

    @Test
    public void initShouldCreatePlayerBehaviorLogTable() throws Exception {
        Path root = Files.createTempDirectory("xechat-behavior-log-schema-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();

        DbInitializer.initIfNeeded();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            Connection conn = session.getConnection();
            assertEquals(1, scalarLong(conn,
                    "SELECT COUNT(1) FROM sqlite_master WHERE type='table' AND name='player_behavior_logs'"));
            assertEquals(1, scalarLong(conn,
                    "SELECT COUNT(1) FROM pragma_table_info('player_behavior_logs') WHERE name='sub_action'"));
            assertEquals(1, scalarLong(conn,
                    "SELECT COUNT(1) FROM pragma_table_info('player_behavior_logs') WHERE name='request_body_json'"));
            assertEquals(1, scalarLong(conn,
                    "SELECT COUNT(1) FROM sqlite_master WHERE type='index' AND name='idx_player_behavior_logs_account_time'"));
        }
    }

    private static long countRows(Connection conn, String tableName) throws Exception {
        return scalarLong(conn, "SELECT COUNT(1) FROM " + tableName);
    }

    private static long scalarLong(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String scalarText(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static void resetFactory() throws Exception {
        Field field = DbInitializer.class.getDeclaredField("FACTORY");
        field.setAccessible(true);
        SqlSessionFactory factory = (SqlSessionFactory) field.get(null);
        if (factory != null) {
            factory.getConfiguration().getEnvironment().getDataSource();
        }
        field.set(null, null);
    }
}
