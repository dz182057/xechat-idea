package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 默契问答答题记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacitQuizRecordDTO implements Serializable {

    private String roomId;

    private long questionId;

    private String question;

    private List<String> options;

    private long createdAt;

    private List<TacitQuizAnswerViewDTO> answers;

    private String opponentKey;

    private String opponentName;

}
