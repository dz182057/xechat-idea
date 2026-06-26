package cn.xeblog.server.account;

import cn.xeblog.server.config.GlobalConfig;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * 账号体系数据库初始化器(SQLite + MyBatis,无 Spring)。
 *
 * <p>启动时调用 {@link #factory()} 触发懒加载初始化:
 * 1. 确保 data 目录存在;
 * 2. 构建 SqlSessionFactory(注入 jdbc.url);
 * 3. 检测 accounts 表不存在则跑 schema.sql。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
public final class DbInitializer {

    private static final String QUICK_QUIZ_TO_TACIT_QUIZ_MIGRATION = "quick_quiz_to_tacit_quiz_20260618";
    private static final String TACIT_QUIZ_SAME_ANSWERS_BACKFILL = "tacit_quiz_same_answers_backfill_20260625";
    private static final String PET_DAILY_SAYING_RESOURCE = "pet_daily_saying_content_v1.json";

    private static volatile SqlSessionFactory FACTORY;

    private DbInitializer() {
    }

    /**
     * 获取全局唯一的 SqlSessionFactory(首次调用触发初始化)
     */
    public static SqlSessionFactory factory() {
        if (FACTORY == null) {
            synchronized (DbInitializer.class) {
                if (FACTORY == null) {
                    init();
                }
            }
        }
        return FACTORY;
    }

    /**
     * 显式触发初始化(供 main 启动钩子调用,避免懒加载延迟到首次请求)
     */
    public static void initIfNeeded() {
        factory();
    }

    private static void init() {
        try {
            // 1. 确保数据目录存在
            Files.createDirectories(Paths.get(GlobalConfig.DATA_DIR));
            Files.createDirectories(Paths.get(GlobalConfig.AVATAR_DIR));

            // 2. 构建 SqlSessionFactory,把 jdbc.url 通过 properties 注入到 mybatis-config.xml
            Properties props = new Properties();
            props.setProperty("jdbc.url", "jdbc:sqlite:" + GlobalConfig.DB_PATH);

            try (InputStream cfg = Resources.getResourceAsStream("mybatis-config.xml")) {
                FACTORY = new SqlSessionFactoryBuilder().build(cfg, props);
            }

            // 3. 首次启动建表
            ensureSchema();
            ensureLoginLogsTable();
            ensurePlayerBehaviorLogTable();
            ensureMessageColumns();
            ensureFriendTables();
            ensureDrawGuessWordTable();
            ensureQuickQuizTables();
            ensureTurtleSoupTables();
            ensurePetTables();
            backfillTacitQuizSameAnswers();
            ensurePushSubscriptionTable();
            ensurePetTables();

            log.info("账号体系数据库就绪: {}", GlobalConfig.DB_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("账号体系数据库初始化失败", e);
        }
    }

    /**
     * 检测 accounts 表是否存在,不存在则执行 schema.sql
     */
    private static void ensureSchema() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();

            if (tableExists(conn, "accounts")) {
                return;
            }

            log.info("首次启动,执行 db/schema.sql 建库...");
            String sql = stripLineComments(loadResource("db/schema.sql"));

            try (Statement st = conn.createStatement()) {
                // sqlite-jdbc 不支持单个 execute 多语句,按 ; 切开依次执行
                // (split 前已剥掉 -- 行注释,避免注释里的中文 ";" 把 SQL 切碎)
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    st.execute(trimmed);
                }
            }
            log.info("schema.sql 执行完毕");
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 给已有数据库补齐登录记录表。
     */
    private static void ensureLoginLogsTable() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                if (tableExists(conn, "login_logs")) {
                    return;
                }
                st.execute("CREATE TABLE login_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "account_id INTEGER," +
                        "ip TEXT," +
                        "region TEXT," +
                        "platform TEXT," +
                        "success INTEGER NOT NULL," +
                        "fail_reason TEXT," +
                        "created_at INTEGER NOT NULL" +
                        ")");
                log.info("数据库迁移: 创建 login_logs 表");
            }
        }
    }

    /**
     * 给已有数据库补齐玩家行为流水表。
     */
    private static void ensurePlayerBehaviorLogTable() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS player_behavior_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "account_id INTEGER," +
                        "account TEXT," +
                        "nickname TEXT," +
                        "guest INTEGER NOT NULL DEFAULT 0," +
                        "platform TEXT," +
                        "client_uuid TEXT," +
                        "ip TEXT," +
                        "region TEXT," +
                        "action TEXT NOT NULL," +
                        "sub_action TEXT," +
                        "protocol TEXT," +
                        "result_status TEXT," +
                        "error_message TEXT," +
                        "request_body_json TEXT," +
                        "related_type TEXT," +
                        "related_id TEXT," +
                        "created_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_player_behavior_logs_account_time " +
                        "ON player_behavior_logs(account_id, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_player_behavior_logs_action_time " +
                        "ON player_behavior_logs(action, sub_action, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_player_behavior_logs_created " +
                        "ON player_behavior_logs(created_at)");
            }
        }
    }

    /**
     * 给已有 SQLite 数据库补齐聊天字段。SQLite 的 IF NOT EXISTS 不覆盖已存在表结构,
     * 因此这里做轻量迁移。
     */
    private static void ensureMessageColumns() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                addColumnIfMissing(conn, st, "messages_public", "quote_json", "TEXT");
                addColumnIfMissing(conn, st, "messages_public", "recalled_at", "INTEGER");
                addColumnIfMissing(conn, st, "messages_private", "quote_message_id", "INTEGER");
                addColumnIfMissing(conn, st, "messages_private", "recalled_at", "INTEGER");
            }
        }
    }

    /**
     * 给已有数据库补齐好友和隐身字段。
     */
    private static void ensureFriendTables() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                addColumnIfMissing(conn, st, "accounts", "stealth", "INTEGER NOT NULL DEFAULT 0");
                st.execute("CREATE TABLE IF NOT EXISTS friends (" +
                        "owner_account_id INTEGER NOT NULL," +
                        "friend_account_id INTEGER NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "PRIMARY KEY (owner_account_id, friend_account_id)" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_friends_friend ON friends(friend_account_id)");
                st.execute("CREATE TABLE IF NOT EXISTS friend_requests (" +
                        "id INTEGER PRIMARY KEY," +
                        "from_account_id INTEGER NOT NULL," +
                        "to_account_id INTEGER NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'PENDING'," +
                        "created_at INTEGER NOT NULL," +
                        "handled_at INTEGER," +
                        "UNIQUE (from_account_id, to_account_id, status)" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_friend_requests_to_status " +
                        "ON friend_requests(to_account_id, status, created_at)");
            }
        }
    }

    /**
     * 给已有数据库补齐你画我猜词库表。
     */
    private static void ensureDrawGuessWordTable() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                if (tableExists(conn, "draw_guess_words")) {
                    return;
                }
                st.execute("CREATE TABLE draw_guess_words (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "word TEXT NOT NULL UNIQUE," +
                        "hint TEXT," +
                        "sort_order INTEGER NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                log.info("数据库迁移: 创建 draw_guess_words 表");
            }
        }
    }

    /**
     * 给已有数据库补齐快问快答题库和答题记录表。
     */
    private static void ensureQuickQuizTables() throws Exception {
        try (SqlSession session = FACTORY.openSession(false)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS db_migrations (" +
                        "id TEXT PRIMARY KEY," +
                        "applied_at INTEGER NOT NULL" +
                        ")");
                if (!tableExists(conn, "quick_quiz_questions")) {
                    st.execute("CREATE TABLE quick_quiz_questions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "question TEXT NOT NULL UNIQUE," +
                            "options_json TEXT NOT NULL," +
                            "correct_answer_index INTEGER NOT NULL DEFAULT 0," +
                            "score INTEGER NOT NULL DEFAULT 1," +
                            "sort_order INTEGER NOT NULL DEFAULT 0," +
                            "active INTEGER NOT NULL DEFAULT 1," +
                            "created_at INTEGER NOT NULL," +
                            "updated_at INTEGER NOT NULL" +
                            ")");
                    log.info("数据库迁移: 创建 quick_quiz_questions 表");
                } else {
                    addColumnIfMissing(conn, st, "quick_quiz_questions", "correct_answer_index", "INTEGER NOT NULL DEFAULT 0");
                    addColumnIfMissing(conn, st, "quick_quiz_questions", "score", "INTEGER NOT NULL DEFAULT 1");
                }
                if (!tableExists(conn, "quick_quiz_records")) {
                    st.execute("CREATE TABLE quick_quiz_records (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "room_id TEXT NOT NULL," +
                            "question_id INTEGER NOT NULL," +
                            "player_key TEXT NOT NULL," +
                            "username TEXT NOT NULL," +
                            "choice_index INTEGER NOT NULL," +
                            "choice_text TEXT NOT NULL," +
                            "correct INTEGER NOT NULL DEFAULT 0," +
                            "points_delta INTEGER NOT NULL DEFAULT 0," +
                            "total_score INTEGER NOT NULL DEFAULT 0," +
                            "created_at INTEGER NOT NULL" +
                            ")");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_quick_quiz_records_player " +
                            "ON quick_quiz_records(player_key, question_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_quick_quiz_records_room_question " +
                            "ON quick_quiz_records(room_id, question_id)");
                    log.info("数据库迁移: 创建 quick_quiz_records 表");
                } else {
                    addColumnIfMissing(conn, st, "quick_quiz_records", "correct", "INTEGER NOT NULL DEFAULT 0");
                    addColumnIfMissing(conn, st, "quick_quiz_records", "points_delta", "INTEGER NOT NULL DEFAULT 0");
                    addColumnIfMissing(conn, st, "quick_quiz_records", "total_score", "INTEGER NOT NULL DEFAULT 0");
                }
                ensureTacitQuizTables(conn, st);
                migrateLegacyQuickQuizToTacitQuiz(conn, st);
            }
            session.commit();
        }
    }

    private static void ensureTacitQuizTables(Connection conn, Statement st) throws Exception {
        if (!tableExists(conn, "tacit_quiz_questions")) {
            st.execute("CREATE TABLE tacit_quiz_questions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "question TEXT NOT NULL UNIQUE," +
                    "options_json TEXT NOT NULL," +
                    "sort_order INTEGER NOT NULL DEFAULT 0," +
                    "active INTEGER NOT NULL DEFAULT 1," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")");
            log.info("数据库迁移: 创建 tacit_quiz_questions 表");
        }
        if (!tableExists(conn, "tacit_quiz_records")) {
            st.execute("CREATE TABLE tacit_quiz_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "room_id TEXT NOT NULL," +
                    "question_id INTEGER NOT NULL," +
                    "player_key TEXT NOT NULL," +
                    "username TEXT NOT NULL," +
                    "choice_index INTEGER NOT NULL," +
                    "choice_text TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL" +
                    ")");
            log.info("数据库迁移: 创建 tacit_quiz_records 表");
        }
        st.execute("CREATE INDEX IF NOT EXISTS idx_tacit_quiz_records_player " +
                "ON tacit_quiz_records(player_key, question_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_tacit_quiz_records_room_question " +
                "ON tacit_quiz_records(room_id, question_id)");
    }

    private static void migrateLegacyQuickQuizToTacitQuiz(Connection conn, Statement st) throws Exception {
        if (migrationApplied(conn, QUICK_QUIZ_TO_TACIT_QUIZ_MIGRATION)) {
            return;
        }
        st.execute("INSERT OR IGNORE INTO tacit_quiz_questions (" +
                "id, question, options_json, sort_order, active, created_at, updated_at) " +
                "SELECT id, question, options_json, sort_order, active, created_at, updated_at " +
                "FROM quick_quiz_questions");
        st.execute("INSERT OR IGNORE INTO tacit_quiz_records (" +
                "id, room_id, question_id, player_key, username, choice_index, choice_text, created_at) " +
                "SELECT id, room_id, question_id, player_key, username, choice_index, choice_text, created_at " +
                "FROM quick_quiz_records");
        st.execute("DELETE FROM quick_quiz_records");
        st.execute("DELETE FROM quick_quiz_questions");
        if (tableExists(conn, "sqlite_sequence")) {
            st.execute("DELETE FROM sqlite_sequence WHERE name IN ('quick_quiz_questions', 'quick_quiz_records')");
        }
        markMigrationApplied(conn, QUICK_QUIZ_TO_TACIT_QUIZ_MIGRATION);
        log.info("数据库迁移: 已迁移旧快问快答数据到默契问答表，并清空快问快答表");
    }

    private static void backfillTacitQuizSameAnswers() throws Exception {
        try (SqlSession session = FACTORY.openSession(false)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                if (migrationApplied(conn, TACIT_QUIZ_SAME_ANSWERS_BACKFILL)
                        || !tableExists(conn, "tacit_quiz_records")
                        || !tableExists(conn, "pet_daily_counters")) {
                    return;
                }
                st.execute("INSERT INTO pet_daily_counters (" +
                        "account_id, counter_date, counter, value, updated_at) " +
                        "SELECT account_id, 'lifetime', 'mini_game_tacit_quiz_same_answers', same_count, " +
                        "strftime('%s','now') * 1000 " +
                        "FROM (" +
                        "  SELECT CAST(substr(mine.player_key, 9) AS INTEGER) AS account_id, " +
                        "         COUNT(1) AS same_count " +
                        "  FROM tacit_quiz_records mine " +
                        "  JOIN (" +
                        "    SELECT room_id, question_id " +
                        "    FROM tacit_quiz_records " +
                        "    WHERE choice_index >= 0 " +
                        "    GROUP BY room_id, question_id " +
                        "    HAVING COUNT(1) >= 2 AND COUNT(DISTINCT choice_index) = 1" +
                        "  ) matched ON matched.room_id = mine.room_id " +
                        "      AND matched.question_id = mine.question_id " +
                        "  WHERE mine.choice_index >= 0 " +
                        "    AND mine.player_key GLOB 'account:[0-9]*' " +
                        "  GROUP BY mine.player_key" +
                        ") counts " +
                        "WHERE same_count > 0 " +
                        "ON CONFLICT(account_id, counter_date, counter) DO UPDATE " +
                        "SET value = CASE " +
                        "        WHEN pet_daily_counters.value < excluded.value THEN excluded.value " +
                        "        ELSE pet_daily_counters.value " +
                        "    END, " +
                        "    updated_at = CASE " +
                        "        WHEN pet_daily_counters.value < excluded.value THEN excluded.updated_at " +
                        "        ELSE pet_daily_counters.updated_at " +
                        "    END");
                markMigrationApplied(conn, TACIT_QUIZ_SAME_ANSWERS_BACKFILL);
                log.info("数据库迁移: 已从默契问答历史记录回填同选解锁计数");
            }
            session.commit();
        }
    }

    private static boolean migrationApplied(Connection conn, String migrationId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM db_migrations WHERE id = ?")) {
            ps.setString(1, migrationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void markMigrationApplied(Connection conn, String migrationId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO db_migrations (id, applied_at) VALUES (?, strftime('%s','now') * 1000)")) {
            ps.setString(1, migrationId);
            ps.executeUpdate();
        }
    }

    /**
     * 给已有数据库补齐海龟汤题库和历史记录表。
     */
    private static void ensureTurtleSoupTables() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                if (!tableExists(conn, "turtle_soup_stories")) {
                    st.execute("CREATE TABLE turtle_soup_stories (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "title TEXT," +
                            "surface TEXT NOT NULL UNIQUE," +
                            "bottom TEXT NOT NULL," +
                            "key_clue TEXT," +
                            "difficulty TEXT," +
                            "tags TEXT," +
                            "sort_order INTEGER NOT NULL DEFAULT 0," +
                            "active INTEGER NOT NULL DEFAULT 1," +
                            "created_at INTEGER NOT NULL," +
                            "updated_at INTEGER NOT NULL" +
                            ")");
                    log.info("数据库迁移: 创建 turtle_soup_stories 表");
                } else {
                    addColumnIfMissing(conn, st, "turtle_soup_stories", "title", "TEXT");
                    addColumnIfMissing(conn, st, "turtle_soup_stories", "key_clue", "TEXT");
                    addColumnIfMissing(conn, st, "turtle_soup_stories", "difficulty", "TEXT");
                }
                if (!tableExists(conn, "turtle_soup_records")) {
                    st.execute("CREATE TABLE turtle_soup_records (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "room_id TEXT NOT NULL," +
                            "story_id INTEGER NOT NULL," +
                            "round_no INTEGER NOT NULL," +
                            "host_key TEXT NOT NULL," +
                            "host_name TEXT NOT NULL," +
                            "guesser_key TEXT NOT NULL," +
                            "guesser_name TEXT NOT NULL," +
                            "guess_limit INTEGER NOT NULL," +
                            "guess_used INTEGER NOT NULL," +
                            "result TEXT NOT NULL," +
                            "qa_json TEXT NOT NULL," +
                            "started_at INTEGER NOT NULL," +
                            "ended_at INTEGER NOT NULL" +
                            ")");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_turtle_soup_records_host " +
                            "ON turtle_soup_records(host_key, story_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_turtle_soup_records_guesser " +
                            "ON turtle_soup_records(guesser_key, story_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_turtle_soup_records_room " +
                            "ON turtle_soup_records(room_id, round_no)");
                    log.info("数据库迁移: 创建 turtle_soup_records 表");
                }
            }
        }
    }

    /**
     * 给已有数据库补齐 Web Push 订阅表。
     */
    private static void ensurePushSubscriptionTable() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS push_subscriptions (" +
                        "endpoint TEXT PRIMARY KEY," +
                        "account_id INTEGER NOT NULL," +
                        "p256dh TEXT NOT NULL," +
                        "auth TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_push_subscriptions_account " +
                        "ON push_subscriptions(account_id)");
            }
        }
    }

    /**
     * 给已有数据库补齐狗狗宇宙个人数据表。
     */
    private static void ensurePetTables() throws Exception {
        try (SqlSession session = FACTORY.openSession(true)) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS dogs (" +
                        "id TEXT PRIMARY KEY," +
                        "owner_id INTEGER NOT NULL," +
                        "name TEXT NOT NULL," +
                        "breed TEXT NOT NULL," +
                        "stage TEXT NOT NULL DEFAULT 'puppy'," +
                        "bond INTEGER NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'idle'," +
                        "explore_location TEXT," +
                        "explore_ends_at INTEGER," +
                        "explore_duration_hours INTEGER," +
                        "explore_skill_id TEXT," +
                        "explore_skill_snapshot_id TEXT," +
                        "explore_skill_snapshot_level INTEGER," +
                        "explore_skill_snapshot_version TEXT," +
                        "race_count INTEGER NOT NULL DEFAULT 0," +
                        "race_first_count INTEGER NOT NULL DEFAULT 0," +
                        "weekly_points INTEGER NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_dogs_owner ON dogs(owner_id, created_at)");
                addColumnIfMissing(conn, st, "dogs", "explore_location", "TEXT");
                addColumnIfMissing(conn, st, "dogs", "explore_ends_at", "INTEGER");
                addColumnIfMissing(conn, st, "dogs", "explore_duration_hours", "INTEGER");
                addColumnIfMissing(conn, st, "dogs", "explore_skill_id", "TEXT");
                addColumnIfMissing(conn, st, "dogs", "explore_skill_snapshot_id", "TEXT");
                addColumnIfMissing(conn, st, "dogs", "explore_skill_snapshot_level", "INTEGER");
                addColumnIfMissing(conn, st, "dogs", "explore_skill_snapshot_version", "TEXT");
                addColumnIfMissing(conn, st, "dogs", "race_count", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(conn, st, "dogs", "race_first_count", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(conn, st, "dogs", "weekly_points", "INTEGER NOT NULL DEFAULT 0");
                migrateDogsToV5Shape(conn, st);
                migrateLegacyPetDogs(conn, st);
                st.execute("CREATE TABLE IF NOT EXISTS pet_assets (" +
                        "account_id INTEGER PRIMARY KEY," +
                        "bones INTEGER NOT NULL DEFAULT 300," +
                        "food INTEGER NOT NULL DEFAULT 6," +
                        "makeup_cards INTEGER NOT NULL DEFAULT 0," +
                        "dog_slots INTEGER NOT NULL DEFAULT 1," +
                        "energy INTEGER NOT NULL DEFAULT 10," +
                        "energy_date TEXT NOT NULL DEFAULT '1970-01-01'," +
                        "energy_limit INTEGER NOT NULL DEFAULT 10," +
                        "companion_dog_id TEXT," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                addColumnIfMissing(conn, st, "pet_assets", "energy", "INTEGER NOT NULL DEFAULT 10");
                addColumnIfMissing(conn, st, "pet_assets", "energy_date", "TEXT NOT NULL DEFAULT '1970-01-01'");
                addColumnIfMissing(conn, st, "pet_assets", "companion_dog_id", "TEXT");
                st.execute("CREATE TABLE IF NOT EXISTS pet_items (" +
                        "account_id INTEGER NOT NULL," +
                        "item_id TEXT NOT NULL," +
                        "count INTEGER NOT NULL DEFAULT 0," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY (account_id, item_id)" +
                        ")");
                st.execute("CREATE TABLE IF NOT EXISTS pet_explore_chests (" +
                        "id TEXT PRIMARY KEY," +
                        "account_id INTEGER NOT NULL," +
                        "chest_item_id TEXT NOT NULL," +
                        "location TEXT NOT NULL," +
                        "source_dog_id TEXT," +
                        "source_dog_name TEXT," +
                        "source_dog_breed TEXT," +
                        "duration_hours INTEGER NOT NULL DEFAULT 1," +
                        "skill_snapshot_id TEXT," +
                        "skill_snapshot_level INTEGER," +
                        "skill_snapshot_definition_version TEXT," +
                        "status TEXT NOT NULL DEFAULT 'available'," +
                        "created_at INTEGER NOT NULL," +
                        "opened_at INTEGER" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_explore_chests_account " +
                        "ON pet_explore_chests(account_id, status, chest_item_id, created_at)");
                st.execute("CREATE TABLE IF NOT EXISTS pet_item_ledger (" +
                        "id TEXT PRIMARY KEY," +
                        "account_id INTEGER NOT NULL," +
                        "item_id TEXT NOT NULL," +
                        "quantity INTEGER NOT NULL," +
                        "direction TEXT NOT NULL," +
                        "source TEXT NOT NULL," +
                        "source_ref TEXT," +
                        "metadata_json TEXT," +
                        "created_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_item_ledger_account " +
                        "ON pet_item_ledger(account_id, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_item_ledger_item " +
                        "ON pet_item_ledger(account_id, item_id, created_at)");
                st.execute("CREATE TABLE IF NOT EXISTS game_item_uses (" +
                        "id TEXT PRIMARY KEY," +
                        "game_id TEXT NOT NULL," +
                        "account_id INTEGER NOT NULL," +
                        "item_id TEXT NOT NULL," +
                        "slot TEXT NOT NULL," +
                        "definition_version INTEGER NOT NULL," +
                        "status TEXT NOT NULL," +
                        "target_user_id TEXT," +
                        "payload_json TEXT," +
                        "reward_bones INTEGER NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "settled_at INTEGER" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_game_item_uses_reserved " +
                        "ON game_item_uses(game_id, account_id, item_id, slot, status)");
                st.execute("CREATE TABLE IF NOT EXISTS pet_collections (" +
                        "account_id INTEGER NOT NULL," +
                        "item_id TEXT NOT NULL," +
                        "count INTEGER NOT NULL DEFAULT 0," +
                        "discovered INTEGER NOT NULL DEFAULT 1," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY (account_id, item_id)" +
                        ")");
                st.execute("CREATE TABLE IF NOT EXISTS pet_checkins (" +
                        "account_id INTEGER NOT NULL," +
                        "checkin_date TEXT NOT NULL," +
                        "cycle_day INTEGER NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "PRIMARY KEY (account_id, checkin_date)" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_checkins_account " +
                        "ON pet_checkins(account_id, created_at)");
                st.execute("CREATE TABLE IF NOT EXISTS pet_daily_counters (" +
                        "account_id INTEGER NOT NULL," +
                        "counter_date TEXT NOT NULL," +
                        "counter TEXT NOT NULL," +
                        "value INTEGER NOT NULL DEFAULT 0," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY (account_id, counter_date, counter)" +
                        ")");
                st.execute("CREATE TABLE IF NOT EXISTS pet_training_skills (" +
                        "account_id INTEGER NOT NULL," +
                        "skill_id TEXT NOT NULL," +
                        "level INTEGER NOT NULL DEFAULT 1," +
                        "definition_version TEXT NOT NULL," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY (account_id, skill_id)" +
                        ")");
                st.execute("CREATE TABLE IF NOT EXISTS pet_training_flags (" +
                        "account_id INTEGER PRIMARY KEY," +
                        "first_explore_free_available INTEGER NOT NULL DEFAULT 0," +
                        "first_explore_free_used INTEGER NOT NULL DEFAULT 0," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE TABLE IF NOT EXISTS pet_daily_saying_contents (" +
                        "content_id TEXT PRIMARY KEY," +
                        "category TEXT NOT NULL," +
                        "subtype TEXT," +
                        "title TEXT," +
                        "primary_text TEXT NOT NULL," +
                        "secondary_text TEXT," +
                        "author TEXT," +
                        "work TEXT," +
                        "source_type TEXT," +
                        "source_url TEXT," +
                        "source_locator TEXT," +
                        "source_original TEXT," +
                        "language TEXT," +
                        "translator_editor TEXT," +
                        "tags TEXT," +
                        "tone TEXT," +
                        "recommended_weight REAL NOT NULL DEFAULT 1," +
                        "copyright_status TEXT," +
                        "review_status TEXT NOT NULL," +
                        "risk_notes TEXT," +
                        "active INTEGER NOT NULL DEFAULT 0," +
                        "content_version TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_daily_saying_contents_publish " +
                        "ON pet_daily_saying_contents(category, active, review_status)");
                st.execute("CREATE TABLE IF NOT EXISTS pet_daily_saying_assignments (" +
                        "assignment_id TEXT PRIMARY KEY," +
                        "account_id INTEGER NOT NULL," +
                        "dog_id TEXT NOT NULL," +
                        "dog_name_snapshot TEXT NOT NULL," +
                        "dog_avatar_snapshot TEXT," +
                        "content_id TEXT NOT NULL," +
                        "assigned_server_date TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "assigned_at INTEGER NOT NULL," +
                        "read_at INTEGER," +
                        "read_server_date TEXT," +
                        "greeting_reward_applied INTEGER," +
                        "greeting_intimacy_delta INTEGER," +
                        "content_version TEXT NOT NULL" +
                        ")");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_pet_daily_saying_assignments_day " +
                        "ON pet_daily_saying_assignments(account_id, assigned_server_date)");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_pet_daily_saying_assignments_unread " +
                        "ON pet_daily_saying_assignments(account_id) WHERE status = 'UNREAD'");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_daily_saying_assignments_date_content " +
                        "ON pet_daily_saying_assignments(assigned_server_date, content_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pet_daily_saying_assignments_recent " +
                        "ON pet_daily_saying_assignments(account_id, status, read_at DESC)");
            }
            seedPetDailySayingContent(conn);
        }
    }

    private static void seedPetDailySayingContent(Connection conn) {
        try {
            String raw = loadResource(PET_DAILY_SAYING_RESOURCE);
            JSONObject root = JSONUtil.parseObj(raw);
            String contentVersion = root.getStr("content_version", "pet-daily-saying-v1-2026-06-24");
            JSONArray items = root.getJSONArray("items");
            if (items == null || items.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            int inserted = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO pet_daily_saying_contents (" +
                            "content_id, category, subtype, title, primary_text, secondary_text, author, work, " +
                            "source_type, source_url, source_locator, source_original, language, translator_editor, " +
                            "tags, tone, recommended_weight, copyright_status, review_status, risk_notes, " +
                            "active, content_version, created_at, updated_at" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Object value : items) {
                    JSONObject item = JSONUtil.parseObj(value);
                    ps.setString(1, item.getStr("content_id"));
                    ps.setString(2, item.getStr("category"));
                    ps.setString(3, item.getStr("subtype"));
                    ps.setString(4, item.getStr("title"));
                    ps.setString(5, item.getStr("primary_text"));
                    ps.setString(6, item.getStr("secondary_text"));
                    ps.setString(7, item.getStr("author"));
                    ps.setString(8, item.getStr("work"));
                    ps.setString(9, item.getStr("source_type"));
                    ps.setString(10, item.getStr("source_url"));
                    ps.setString(11, item.getStr("source_locator"));
                    ps.setString(12, item.getStr("source_original"));
                    ps.setString(13, item.getStr("language"));
                    ps.setString(14, item.getStr("translator_editor"));
                    ps.setString(15, item.getStr("tags"));
                    ps.setString(16, item.getStr("tone"));
                    ps.setDouble(17, item.getDouble("recommended_weight", 1D));
                    ps.setString(18, item.getStr("copyright_status"));
                    ps.setString(19, item.getStr("review_status", "待编辑终审"));
                    ps.setString(20, item.getStr("risk_notes"));
                    ps.setInt(21, Boolean.TRUE.equals(item.getBool("active")) ? 1 : 0);
                    ps.setString(22, contentVersion);
                    ps.setLong(23, now);
                    ps.setLong(24, now);
                    inserted += ps.executeUpdate();
                }
            }
            if (inserted > 0) {
                log.info("狗狗每日问候内容库已导入候选内容 {} 条", inserted);
            }
        } catch (Exception e) {
            log.warn("狗狗每日问候内容库初始化跳过: {}", e.getMessage());
        }
    }

    private static void migrateDogsToV5Shape(Connection conn, Statement st) throws Exception {
        if (!tableExists(conn, "dogs")
                || (!columnExists(conn, "dogs", "speed") && !columnExists(conn, "dogs", "energy"))) {
            return;
        }
        st.execute("CREATE TABLE IF NOT EXISTS dogs_v5 (" +
                "id TEXT PRIMARY KEY," +
                "owner_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "breed TEXT NOT NULL," +
                "stage TEXT NOT NULL DEFAULT 'puppy'," +
                "bond INTEGER NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'idle'," +
                "explore_location TEXT," +
                "explore_ends_at INTEGER," +
                "explore_duration_hours INTEGER," +
                "explore_skill_id TEXT," +
                "explore_skill_snapshot_id TEXT," +
                "explore_skill_snapshot_level INTEGER," +
                "explore_skill_snapshot_version TEXT," +
                "race_count INTEGER NOT NULL DEFAULT 0," +
                "race_first_count INTEGER NOT NULL DEFAULT 0," +
                "weekly_points INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        st.execute("INSERT OR IGNORE INTO dogs_v5 (" +
                "id, owner_id, name, breed, stage, bond, status, explore_location, explore_ends_at, " +
                "explore_duration_hours, explore_skill_id, explore_skill_snapshot_id, explore_skill_snapshot_level, " +
                "explore_skill_snapshot_version, race_count, race_first_count, weekly_points, created_at, updated_at) " +
                "SELECT id, owner_id, name, breed, stage, bond, status, explore_location, explore_ends_at, " +
                "explore_duration_hours, explore_skill_id, explore_skill_snapshot_id, explore_skill_snapshot_level, " +
                "explore_skill_snapshot_version, race_count, race_first_count, weekly_points, created_at, updated_at " +
                "FROM dogs");
        st.execute("DROP TABLE dogs");
        st.execute("ALTER TABLE dogs_v5 RENAME TO dogs");
        st.execute("CREATE INDEX IF NOT EXISTS idx_dogs_owner ON dogs(owner_id, created_at)");
        log.info("数据库迁移: dogs 重建为 v5 犬舍表结构");
    }

    private static void migrateLegacyPetDogs(Connection conn, Statement st) throws Exception {
        if (!tableExists(conn, "pet_dogs")) {
            return;
        }
        String weeklyPointsExpr = columnExists(conn, "pet_dogs", "weekly_points") ? "weekly_points" : "0";
        st.execute("INSERT OR IGNORE INTO dogs (" +
                "id, owner_id, name, breed, stage, bond, status, " +
                "explore_location, explore_ends_at, explore_duration_hours, race_count, race_first_count, weekly_points, " +
                "created_at, updated_at) " +
                "SELECT id, account_id, name, breed, stage, bond, status, " +
                "NULL, NULL, NULL, race_count, race_first_count, " + weeklyPointsExpr + ", " +
                "created_at, updated_at FROM pet_dogs");
    }

    private static void addColumnIfMissing(Connection conn, Statement st, String tableName,
                                           String columnName, String columnDef) throws Exception {
        if (!tableExists(conn, tableName) || columnExists(conn, tableName, columnName)) {
            return;
        }
        st.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
        log.info("数据库迁移: {} 增加字段 {}", tableName, columnName);
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + tableName + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 行级剥掉 SQL 中的 "-- ..." 注释。schema.sql 没有以 "--" 起头的字符串字面量,
     * 因此可以安全地按行 strip;否则注释里中文/英文出现的 ";" 会被后续 split(";") 切碎语句。
     */
    private static String stripLineComments(String sql) {
        StringBuilder cleaned = new StringBuilder(sql.length());
        for (String line : sql.split("\\R", -1)) {
            int idx = line.indexOf("--");
            cleaned.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return cleaned.toString();
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = Resources.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到资源: " + path);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
