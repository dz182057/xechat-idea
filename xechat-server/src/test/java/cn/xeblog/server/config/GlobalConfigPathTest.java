package cn.xeblog.server.config;

import cn.xeblog.server.util.ConfigUtil;
import org.junit.After;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class GlobalConfigPathTest {

    private final String originalUserHome = System.getProperty("user.home");

    @After
    public void tearDown() {
        System.clearProperty("xechat.data.path");
        System.setProperty("user.home", originalUserHome);
        ServerConfig.setServerConfig(null);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void shouldKeepOriginalPathsWhenDataPathNotConfigured() throws Exception {
        Path home = Files.createTempDirectory("xechat-default-home");
        System.setProperty("user.home", home.toString());

        GlobalConfig.initDataPath(null);

        assertEquals(home.resolve("xechat").resolve("upload").toString(), GlobalConfig.UPLOAD_FILE_PATH);
        assertEquals(home.resolve("xechat").resolve("data").toString(), GlobalConfig.DATA_DIR);
        assertEquals(home.resolve("xechat").resolve("data").resolve("xechat.db").toString(), GlobalConfig.DB_PATH);
        assertEquals(home.resolve("xechat").resolve("data").resolve("avatars").toString(), GlobalConfig.AVATAR_DIR);
    }

    @Test
    public void shouldUseSystemPropertyAsIndependentDataRoot() throws Exception {
        Path root = Files.createTempDirectory("xechat-custom-root");
        System.setProperty("xechat.data.path", root.toString());

        GlobalConfig.initDataPath(null);

        assertEquals(root.resolve("upload").toString(), GlobalConfig.UPLOAD_FILE_PATH);
        assertEquals(root.resolve("data").toString(), GlobalConfig.DATA_DIR);
        assertEquals(root.resolve("data").resolve("xechat.db").toString(), GlobalConfig.DB_PATH);
        assertEquals(root.resolve("data").resolve("avatars").toString(), GlobalConfig.AVATAR_DIR);
    }

    @Test
    public void shouldReadDataPathFromConfigFile() throws Exception {
        Path root = Files.createTempDirectory("xechat-config-root");
        Path config = Files.createTempFile("xechat-config", ".setting");
        String content = "[SERVER]\n" +
                "port = 1024\n" +
                "enableWS = true\n" +
                "\n" +
                "[DATA]\n" +
                "path = " + root + "\n";
        Files.write(config, content.getBytes(StandardCharsets.UTF_8));

        ServerConfig serverConfig = ConfigUtil.readConfig(new String[]{"-path", config.toString()});

        assertEquals(root.toString(), serverConfig.getDataPath());
    }
}
