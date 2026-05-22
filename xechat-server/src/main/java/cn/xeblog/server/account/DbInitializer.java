package cn.xeblog.server.account;

import cn.xeblog.server.config.GlobalConfig;
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

            log.info("账号体系数据库就绪: {}", GlobalConfig.DB_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("账号体系数据库初始化失败", e);
        }
    }

    /**
     * 检测 accounts 表是否存在,不存在则执行 schema.sql
     */
    private static void ensureSchema() throws Exception {
        try (SqlSession session = FACTORY.openSession(true);
             Connection conn = session.getConnection()) {

            if (tableExists(conn, "accounts")) {
                return;
            }

            log.info("首次启动,执行 db/schema.sql 建库...");
            String sql = loadResource("db/schema.sql");

            try (Statement st = conn.createStatement()) {
                // sqlite-jdbc 不支持单个 execute 多语句,按 ; 切开依次执行
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
