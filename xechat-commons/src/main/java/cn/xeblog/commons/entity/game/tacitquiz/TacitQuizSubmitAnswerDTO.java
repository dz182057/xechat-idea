package cn.xeblog.commons.entity.game.tacitquiz;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 默契问答提交答案。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TacitQuizSubmitAnswerDTO extends GameDTO {

    private long questionId;

    private int choiceIndex;

    private String choiceText;

    private Integer perspectiveGuessChoiceIndex;

    private String perspectiveGuessChoiceText;

    public TacitQuizSubmitAnswerDTO(String roomId, long questionId, int choiceIndex, String choiceText) {
        super(roomId, Game.TACIT_QUIZ);
        this.questionId = questionId;
        this.choiceIndex = choiceIndex;
        this.choiceText = choiceText;
    }

    public TacitQuizSubmitAnswerDTO(String roomId, long questionId, int choiceIndex, String choiceText,
                                    Integer perspectiveGuessChoiceIndex, String perspectiveGuessChoiceText) {
        this(roomId, questionId, choiceIndex, choiceText);
        this.perspectiveGuessChoiceIndex = perspectiveGuessChoiceIndex;
        this.perspectiveGuessChoiceText = perspectiveGuessChoiceText;
    }

}
