package cn.xeblog.commons.entity.game.tacitquiz;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 默契问答请求下一题。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TacitQuizNextQuestionDTO extends GameDTO {

    public TacitQuizNextQuestionDTO(String roomId) {
        super(roomId, Game.TACIT_QUIZ);
    }

}
