package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 快问快答单个玩家答案。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizAnswerViewDTO implements Serializable {

    private String playerKey;

    private String username;

    private int choiceIndex;

    private String choiceText;

    private long answeredAt;

}
