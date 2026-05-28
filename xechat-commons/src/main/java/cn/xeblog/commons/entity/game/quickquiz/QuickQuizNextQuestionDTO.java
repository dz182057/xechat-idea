package cn.xeblog.commons.entity.game.quickquiz;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 快问快答请求下一题。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QuickQuizNextQuestionDTO extends GameDTO {

    public QuickQuizNextQuestionDTO(String roomId) {
        super(roomId, Game.QUICK_QUIZ);
    }

}
