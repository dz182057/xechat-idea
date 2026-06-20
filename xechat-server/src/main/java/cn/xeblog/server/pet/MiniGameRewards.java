package cn.xeblog.server.pet;

import cn.xeblog.commons.enums.Game;

import java.util.List;

/**
 * 小游戏基础产出与房间级彩蛋奖励入口。
 */
@FunctionalInterface
public interface MiniGameRewards {

    void apply(long accountId, Game game, boolean win, long durationSeconds);

    default void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
    }

    static MiniGameRewards petService() {
        return new MiniGameRewards() {
            @Override
            public void apply(long accountId, Game game, boolean win, long durationSeconds) {
                PetService.applyMiniGameResult(accountId, game, win, durationSeconds);
            }

            @Override
            public void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
                PetService.applyMiniGameRoomBonus(game, accountIds, durationSeconds);
            }
        };
    }
}
