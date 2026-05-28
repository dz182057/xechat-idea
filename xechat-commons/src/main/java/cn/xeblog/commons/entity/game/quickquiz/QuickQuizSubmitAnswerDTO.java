package cn.xeblog.commons.entity.game.quickquiz;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 快问快答提交答案。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QuickQuizSubmitAnswerDTO extends GameDTO {

    private long questionId;

    private int choiceIndex;

    private String choiceText;

    public QuickQuizSubmitAnswerDTO(String roomId, long questionId, int choiceIndex, String choiceText) {
        super(roomId, Game.QUICK_QUIZ);
        this.questionId = questionId;
        this.choiceIndex = choiceIndex;
        this.choiceText = choiceText;
    }

}
