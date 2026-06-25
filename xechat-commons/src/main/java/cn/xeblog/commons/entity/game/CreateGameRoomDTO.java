package cn.xeblog.commons.entity.game;

import cn.xeblog.commons.enums.Game;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author anlingyi
 * @date 2022/5/25 10:22 上午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateGameRoomDTO implements Serializable {

    /**
     * 当前游戏
     */
    private Game game;

    /**
     * 几人房
     */
    private int nums;

    /**
     * 游戏模式
     */
    private String gameMode;

    /**
     * 你画我猜局数：每局所有玩家轮流画一次
     */
    private int drawGuessRoundCount;

    /**
     * 你画我猜每题限时秒数
     */
    private int drawGuessTimeLimitSeconds;

    /**
     * 快问快答本局答题数
     */
    private int quickQuizQuestionCount;

    /**
     * 快问快答每题限时秒数
     */
    private int quickQuizTimeLimitSeconds;

    /**
     * 快问快答报名骨头币
     */
    private int quickQuizEntryFee;

    /**
     * 默契问答本局答题数
     */
    private int tacitQuizQuestionCount;

    /**
     * 海龟汤猜底机会
     */
    private int turtleSoupGuessLimit;

    /**
     * 海龟汤首轮主持人：OWNER / GUEST / RANDOM
     */
    private String turtleSoupHostMode;

    /**
     * 狗狗赛跑模式：pure_betting / owned_dog
     */
    private String dogRaceMode;

    /**
     * 狗狗大战赛制：1 / 3 / 5 / 7
     */
    private int dogBattleRoundCount;

    /**
     * 狗狗大战是否允许技能
     */
    private Boolean dogBattleAllowSkill;

    /**
     * 房主玩家级狗狗道具声明。
     */
    private GamePlayerPetItemsDTO petItems;

    public CreateGameRoomDTO(Game game, int nums, String gameMode) {
        this.game = game;
        this.nums = nums;
        this.gameMode = gameMode;
    }

    public CreateGameRoomDTO(Game game, int nums, String gameMode, int quickQuizQuestionCount) {
        this.game = game;
        this.nums = nums;
        this.gameMode = gameMode;
        this.quickQuizQuestionCount = quickQuizQuestionCount;
        this.tacitQuizQuestionCount = quickQuizQuestionCount;
    }

}
