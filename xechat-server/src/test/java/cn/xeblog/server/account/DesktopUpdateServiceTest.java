package cn.xeblog.server.account;

import cn.xeblog.commons.entity.DesktopUpdateInfoDTO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DesktopUpdateServiceTest {

    private Path updateDir;

    @Before
    public void setUp() throws Exception {
        updateDir = Files.createTempDirectory("xechat-desktop-updates");
        System.setProperty("xechat.desktopUpdate.dir", updateDir.toString());
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("xechat.desktopUpdate.dir");
        if (updateDir != null && Files.exists(updateDir)) {
            Files.walk(updateDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    public void enableShouldRestoreDisabledVersionAndRewriteLatest() throws Exception {
        publish("1.0.0");
        publish("1.0.1");
        assertTrue(DesktopUpdateService.disable("1.0.1"));

        boolean changed = DesktopUpdateService.enable("1.0.1");

        assertTrue(changed);
        DesktopUpdateInfoDTO latest = DesktopUpdateService.latest(null);
        assertNotNull(latest);
        assertEquals("1.0.1", latest.getVersion());
        assertTrue(Files.readAllLines(updateDir.resolve("versions.json"), StandardCharsets.UTF_8)
                .stream()
                .anyMatch(line -> line.contains("\"version\": \"1.0.1\"")));
        assertTrue(Files.readAllLines(updateDir.resolve("latest.yml"), StandardCharsets.UTF_8)
                .stream()
                .anyMatch(line -> line.contains("1.0.1")));
    }

    @Test
    public void deleteShouldRemoveVersionDirectoryAndIndexEntry() throws Exception {
        publish("1.0.0");
        publish("1.0.1");

        boolean changed = DesktopUpdateService.delete("1.0.1");

        assertTrue(changed);
        assertFalse(Files.exists(updateDir.resolve("1.0.1")));
        List<DesktopUpdateInfoDTO> list = DesktopUpdateService.list(null);
        assertEquals(1, list.size());
        assertEquals("1.0.0", list.get(0).getVersion());
        assertEquals("1.0.0", DesktopUpdateService.latest(null).getVersion());
        assertFalse(Files.readAllLines(updateDir.resolve("versions.json"), StandardCharsets.UTF_8)
                .stream()
                .anyMatch(line -> line.contains("1.0.1")));
    }

    private void publish(String version) throws Exception {
        String installerName = "XeChat-" + version + "-setup.exe";
        String latest = "version: " + version + "\n" +
                "path: " + installerName + "\n";
        byte[] installer = ("installer-" + version).getBytes(StandardCharsets.UTF_8);
        byte[] blockMap = ("blockmap-" + version).getBytes(StandardCharsets.UTF_8);
        DesktopUpdateService.publish(
                version,
                "XeChat " + version,
                "测试版本",
                false,
                "latest.yml",
                latest.getBytes(StandardCharsets.UTF_8),
                installerName,
                installer,
                installerName + ".blockmap",
                blockMap
        );
    }
}
