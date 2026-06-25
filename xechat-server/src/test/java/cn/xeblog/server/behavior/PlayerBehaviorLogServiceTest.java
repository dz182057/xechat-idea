package cn.xeblog.server.behavior;

import cn.xeblog.commons.entity.Request;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.PetAction;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.Protocol;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerBehaviorLogServiceTest {

    @After
    public void tearDown() throws Exception {
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void recordShouldPersistActionAndPetSubAction() throws Exception {
        Path root = Files.createTempDirectory("xechat-behavior-log-service-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();

        User user = new User();
        user.setAccountId(1001L);
        user.setAccount("alice");
        user.setNickname("小爱");
        user.setUuid("client-uuid-1");
        user.setPlatform(Platform.WEB);
        user.setIp("127.0.0.1");
        user.setGuest(false);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dogId", "dog-1");
        PetRequestDTO petBody = new PetRequestDTO(PetAction.FEED, 7L, content);
        Request<PetRequestDTO> request = new Request<>(petBody, Action.PET);
        request.setProtocol(Protocol.DEFAULT);

        PlayerBehaviorLogService.record(user, request, "OK", null);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + GlobalConfig.DB_PATH);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM player_behavior_logs")) {
            assertTrue(rs.next());
            assertEquals(1001L, rs.getLong("account_id"));
            assertEquals("alice", rs.getString("account"));
            assertEquals("小爱", rs.getString("nickname"));
            assertEquals("WEB", rs.getString("platform"));
            assertEquals("client-uuid-1", rs.getString("client_uuid"));
            assertEquals("127.0.0.1", rs.getString("ip"));
            assertEquals("PET", rs.getString("action"));
            assertEquals("FEED", rs.getString("sub_action"));
            assertEquals("DEFAULT", rs.getString("protocol"));
            assertEquals("OK", rs.getString("result_status"));
            assertTrue(rs.getString("request_body_json").contains("\"dogId\":\"dog-1\""));
        }
    }

    @Test
    public void recordShouldMaskSensitiveRequestFields() throws Exception {
        Path root = Files.createTempDirectory("xechat-behavior-log-mask-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", "alice");
        body.put("password", "plain-password");
        body.put("token", "session-token");
        body.put("avatarBase64", "large-avatar-body");
        body.put("roomId", 123);
        Request<Map<String, Object>> request = new Request<>(body, Action.LOGIN);
        request.setProtocol(Protocol.DEFAULT);

        PlayerBehaviorLogService.record(null, "127.0.0.1", request, "HANDLED", null);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + GlobalConfig.DB_PATH);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT request_body_json FROM player_behavior_logs")) {
            assertTrue(rs.next());
            String json = rs.getString("request_body_json");
            assertTrue(json.contains("\"account\":\"alice\""));
            assertTrue(json.contains("\"roomId\":123"));
            assertTrue(json.contains("\"password\":\"[已脱敏]\""));
            assertTrue(json.contains("\"token\":\"[已脱敏]\""));
            assertTrue(json.contains("\"avatarBase64\":\"[已脱敏]\""));
            assertFalse(json.contains("plain-password"));
            assertFalse(json.contains("session-token"));
            assertFalse(json.contains("large-avatar-body"));
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
