package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 默契问答答案揭示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacitQuizAnswerResultDTO implements Serializable {

    private String roomId;

    private TacitQuizQuestionDTO question;

    private List<TacitQuizAnswerViewDTO> answers;

    private int roundNo;

    private int totalRounds;

    private boolean finished;

    private List<String> petItemNotices;

}
