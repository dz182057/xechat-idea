package cn.xeblog.server.push;

import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class PushSubscriptionServiceTest {

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-push-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();
    }

    @After
    public void tearDown() throws Exception {
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void unsubscribeShouldAllowSameAccountToSubscribeAgain() {
        PushSubscriptionService.upsert(100L, "https://push.example/one", "key-1", "auth-1");
        assertEquals(1, PushSubscriptionService.listByAccount(100L).size());

        PushSubscriptionService.delete(100L, "https://push.example/one");
        assertEquals(0, PushSubscriptionService.listByAccount(100L).size());

        PushSubscriptionService.upsert(100L, "https://push.example/two", "key-2", "auth-2");
        assertEquals(1, PushSubscriptionService.listByAccount(100L).size());
        assertEquals("https://push.example/two", PushSubscriptionService.listByAccount(100L).get(0).getEndpoint());
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
