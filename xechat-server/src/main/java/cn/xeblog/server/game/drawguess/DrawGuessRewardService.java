package cn.xeblog.server.game.drawguess;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.MiniGameRewards;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 你画我猜小游戏基础产出结算。
 */
@Slf4j
public final class DrawGuessRewardService {

    private static final Map<String, RoomState> STATES = new ConcurrentHashMap<>();
    private static MiniGameRewards miniGameRewards = MiniGameRewards.petService();
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private DrawGuessRewardService() {
    }

    public static void handleStart(GameRoom room) {
        if (room == null || room.getId() == null) {
            return;
        }
        RoomState state = new RoomState();
        state.startedAt = nowSupplier.getAsLong();
        STATES.put(roomKey(room.getId()), state);
    }

    public static void handleCorrect(GameRoom room, DrawGuessDTO dto) {
        if (room == null || room.getId() == null || dto == null) {
            return;
        }
        RoomState state = STATES.computeIfAbsent(roomKey(room.getId()), key -> {
            RoomState fallback = new RoomState();
            fallback.startedAt = nowSupplier.getAsLong();
            return fallback;
        });
        if (state.applied) {
            return;
        }
        state.applied = true;
        String winnerKey = resolveGuesserKey(room, dto);
        long durationSeconds = Math.max(0L, (nowSupplier.getAsLong() - state.startedAt + 999L) / 1000L);
        List<Long> accountIds = new ArrayList<>();
        for (GameRoom.Player player : room.getUsers().values()) {
            if (player.getAccountId() <= 0) {
                continue;
            }
            accountIds.add(player.getAccountId());
            boolean win = player.getId().equals(winnerKey);
            try {
                miniGameRewards.apply(player.getAccountId(), Game.DRAW_GUESS, win, durationSeconds);
            } catch (RuntimeException e) {
                log.warn("你画我猜小游戏产出结算失败 -> accountId: {}", player.getAccountId(), e);
            }
        }
        try {
            miniGameRewards.applyRoomBonus(Game.DRAW_GUESS, accountIds, durationSeconds);
        } catch (RuntimeException e) {
            log.warn("你画我猜房间级彩蛋奖励结算失败 -> accountIds: {}", accountIds, e);
        }
    }

    public static void clearRoom(String roomId) {
        STATES.remove(roomKey(roomId));
    }

    public static void setMiniGameRewardsForTest(MiniGameRewards testMiniGameRewards) {
        miniGameRewards = testMiniGameRewards == null ? MiniGameRewards.petService() : testMiniGameRewards;
    }

    public static void resetMiniGameRewards() {
        miniGameRewards = MiniGameRewards.petService();
    }

    public static void setNowSupplierForTest(LongSupplier testNowSupplier) {
        nowSupplier = testNowSupplier == null ? System::currentTimeMillis : testNowSupplier;
    }

    public static void resetNowSupplier() {
        nowSupplier = System::currentTimeMillis;
    }

    private static String resolveGuesserKey(GameRoom room, DrawGuessDTO dto) {
        String guesserId = trimToNull(dto.getGuesserId());
        if (guesserId != null && room.getUsers().containsKey(guesserId)) {
            return guesserId;
        }
        String guesserName = trimToNull(dto.getGuesserName());
        if (guesserName == null) {
            return null;
        }
        for (GameRoom.Player player : room.getUsers().values()) {
            if (guesserName.equals(player.getUsername()) || guesserName.equals(player.getNickname())) {
                return player.getId();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String roomKey(String roomId) {
        return roomId == null ? "" : roomId;
    }

    private static class RoomState {
        private long startedAt;
        private boolean applied;
    }

}
