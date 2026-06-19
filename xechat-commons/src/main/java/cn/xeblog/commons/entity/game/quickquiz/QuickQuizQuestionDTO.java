package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 快问快答题目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizQuestionDTO implements Serializable {

    private long id;

    private String question;

    private List<String> options;

    private int correctAnswerIndex;

    private int score;

    private long startedAt;

    private long deadlineAt;

    private int roundNo;

    private int totalRounds;

    private String petItemNotice;

    private Integer petItemDisabledOptionIndex;

    public QuickQuizQuestionDTO(long id, String question, List<String> options, int correctAnswerIndex, int score,
                                long startedAt, long deadlineAt, int roundNo, int totalRounds) {
        this.id = id;
        this.question = question;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
        this.score = score;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.roundNo = roundNo;
        this.totalRounds = totalRounds;
    }

}
