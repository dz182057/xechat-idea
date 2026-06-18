package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 快问快答玩家积分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizPlayerScoreDTO implements Serializable {

    private String playerKey;

    private String username;

    private int score;

    private boolean winner;

    private int rewardBones;

}
