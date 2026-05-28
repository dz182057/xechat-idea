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

    private long startedAt;

    private long deadlineAt;

    private int roundNo;

    private int totalRounds;

}
