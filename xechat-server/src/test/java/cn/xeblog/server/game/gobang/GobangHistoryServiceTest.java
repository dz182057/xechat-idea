package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.config.GlobalConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GobangHistoryServiceTest {

    private Path tempRoot;

    @After
    public void tearDown() throws Exception {
        GobangPetItemService.clearRoom("history-room");
        GobangPetItemService.resetNowSupplier();
        GobangHistoryService.resetForTest();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
        if (tempRoot != null) {
            deleteDirectory(tempRoot);
        }
    }

    @Test
    public void recordsWholeGobangStateToJsonLines() throws Exception {
        long now = Instant.parse("2026-07-02T06:00:00Z").toEpochMilli();
        initTempDataPath(now);
        User homeowner = user(4101L, "history-home-channel", "房主");
        User opponent = user(4102L, "history-opponent-channel", "对手");
        GameRoom room = room("history-room", homeowner, opponent);

        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 2));
        GobangDTO accepted = GobangPetItemService.handleMove(homeowner, room, new GobangDTO(7, 7, 1));
        GobangDTO rejected = GobangPetItemService.rejectedMove(opponent, room, new GobangDTO(7, 7, 2));

        Assert.assertNotNull(accepted);
        Assert.assertNotNull(rejected);
        List<String> lines = Files.readAllLines(historyFile("history-room", now), StandardCharsets.UTF_8);
        Assert.assertEquals(3, lines.size());
        Assert.assertTrue(lines.get(0).contains("\"event\":\"START\""));
        Assert.assertTrue(lines.get(1).contains("\"event\":\"MOVE\""));
        Assert.assertTrue(lines.get(1).contains("\"moveHistory\""));
        Assert.assertTrue(lines.get(1).contains("\"board\""));
        Assert.assertTrue(lines.get(1).contains("\"x\":7"));
        Assert.assertTrue(lines.get(1).contains("\"y\":7"));
        Assert.assertTrue(lines.get(1).contains("\"playerTypes\""));
        Assert.assertTrue(lines.get(2).contains("\"event\":\"REJECTED\""));
        Assert.assertTrue(lines.get(2).contains("\"server_rejected\""));
    }

    @Test
    public void cleanupKeepsOnlyRecentHistoryDays() throws Exception {
        long now = Instant.parse("2026-07-02T06:00:00Z").toEpochMilli();
        initTempDataPath(now);
        GobangHistoryService.setRetentionDaysForTest(3);
        GobangHistoryService.setCleanupIntervalMillisForTest(0);
        Path oldDir = tempRoot.resolve(GobangHistoryService.HISTORY_DIR_NAME).resolve("2026-06-28");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("old-room.jsonl"), "{}\n", StandardCharsets.UTF_8);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "TEST");
        payload.put("roomId", "fresh-room");
        GobangHistoryService.record("fresh-room", payload);

        Assert.assertFalse(Files.exists(oldDir));
        Assert.assertTrue(Files.exists(historyFile("fresh-room", now)));
    }

    private void initTempDataPath(long now) throws Exception {
        tempRoot = Files.createTempDirectory("xechat-gobang-history-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempRoot.toString());
        GlobalConfig.initDataPath(null);
        GobangPetItemService.setNowSupplierForTest(() -> now);
        GobangHistoryService.setNowSupplierForTest(() -> now);
        GobangHistoryService.setCleanupIntervalMillisForTest(0);
    }

    private Path historyFile(String roomId, long now) {
        return tempRoot.resolve(GobangHistoryService.HISTORY_DIR_NAME)
                .resolve(GobangHistoryService.dateFolder(now))
                .resolve(roomId + ".jsonl");
    }

    private static GameRoom room(String roomId, User homeowner, User opponent) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.GOBANG);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.addUser(homeowner);
        room.addUser(opponent);
        return room;
    }

    private static User user(long accountId, String channelId, String nickname) {
        User user = new User();
        user.setAccountId(accountId);
        user.setAccount("account" + accountId);
        user.setId(channelId);
        user.setNickname(nickname);
        return user;
    }

    private static void deleteDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 测试清理失败不影响断言结果。
                }
            });
        }
    }
}
