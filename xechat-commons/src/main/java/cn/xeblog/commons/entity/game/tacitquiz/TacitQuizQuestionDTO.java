package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 默契问答题目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacitQuizQuestionDTO implements Serializable {

    private long id;

    private String question;

    private List<String> options;

    private long startedAt;

    private long deadlineAt;

    private int roundNo;

    private int totalRounds;

}
