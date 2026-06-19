package cn.xeblog.server.game.turtlesoup;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TurtleSoupServiceTest {

    private final List<String> miniGameEvents = new ArrayList<>();

    @Before
    public void setUp() {
        TurtleSoupService.setMiniGameRewardsForTest((accountId, game, win, durationSeconds) ->
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds));
    }

    @After
    public void tearDown() {
        TurtleSoupService.resetMiniGameRewards();
    }

    @Test
    public void finishedCorrectRoundShouldApplyMiniGameRewardsToHostAndGuesser() {
        GameRoom room = room("turtle-mini-game", Arrays.asList(
                user("channel-host", 1L, "主持人"),
                user("channel-guesser", 2L, "猜题人")
        ));

        TurtleSoupService.applyMiniGameRewardsForRound(
                room,
                "account:1",
                "account:2",
                TurtleSoupDTO.GuessResult.CORRECT,
                1_000L,
                61_001L);

        assertEquals(Arrays.asList(
                "1:" + Game.TURTLE_SOUP + ":false:61",
                "2:" + Game.TURTLE_SOUP + ":true:61"), miniGameEvents);
    }

    @Test
    public void previewDtoForHostShouldHideKeyClue() {
        TurtleSoupDTO dto = previewDto();

        TurtleSoupService.sanitizePreview(dto, true);

        assertNull("主持人选题预览时不应提前拿到关键线索", dto.getKeyClue());
        assertEquals("主持人预览仍应看到汤面", "汤面", dto.getSurface());
    }

    @Test
    public void previewDtoForGuesserShouldHideStoryDetails() {
        TurtleSoupDTO dto = previewDto();

        TurtleSoupService.sanitizePreview(dto, false);

        assertNull("猜题人预览等待时不应拿到标题", dto.getTitle());
        assertNull("猜题人预览等待时不应拿到汤面", dto.getSurface());
        assertNull("猜题人预览等待时不应拿到关键线索", dto.getKeyClue());
    }

    private TurtleSoupDTO previewDto() {
        TurtleSoupDTO dto = new TurtleSoupDTO("room-1");
        dto.setEvent(TurtleSoupDTO.Event.PREVIEW_STORY);
        dto.setTitle("标题");
        dto.setSurface("汤面");
        dto.setBottom("汤底");
        dto.setKeyClue("关键线索");
        dto.setDifficulty("简单");
        dto.setTags("测试");
        return dto;
    }

    private GameRoom room(String roomId, List<User> users) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.TURTLE_SOUP);
        room.setNums(users.size());
        room.setHomeowner(users.get(0));
        for (User user : users) {
            room.getUsers().put(user.getIdentityKey(), new GameRoom.Player(user));
        }
        return room;
    }

    private User user(String channelId, long accountId, String name) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setNickname(name);
        user.setUsername(name);
        return user;
    }
}
