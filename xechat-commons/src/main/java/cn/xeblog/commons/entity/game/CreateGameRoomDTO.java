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
     * 快问快答本局答题数
     */
    private int quickQuizQuestionCount;

    /**
     * 海龟汤猜底机会
     */
    private int turtleSoupGuessLimit;

    /**
     * 海龟汤首轮主持人：OWNER / GUEST / RANDOM
     */
    private String turtleSoupHostMode;

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
    }

}
