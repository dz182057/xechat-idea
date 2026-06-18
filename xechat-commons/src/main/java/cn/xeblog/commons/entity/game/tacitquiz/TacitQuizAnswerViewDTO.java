package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 默契问答单个玩家答案。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacitQuizAnswerViewDTO implements Serializable {

    private String playerKey;

    private String username;

    private int choiceIndex;

    private String choiceText;

    private long answeredAt;

}
