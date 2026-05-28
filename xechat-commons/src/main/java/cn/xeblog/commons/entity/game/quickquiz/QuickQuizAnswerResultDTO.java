package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 快问快答答案揭示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizAnswerResultDTO implements Serializable {

    private String roomId;

    private QuickQuizQuestionDTO question;

    private List<QuickQuizAnswerViewDTO> answers;

    private int roundNo;

    private int totalRounds;

    private boolean finished;

}
