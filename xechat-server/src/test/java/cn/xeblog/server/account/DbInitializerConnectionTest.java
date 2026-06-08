package cn.xeblog.server.account;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.xeblog.server.config.GlobalConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

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
