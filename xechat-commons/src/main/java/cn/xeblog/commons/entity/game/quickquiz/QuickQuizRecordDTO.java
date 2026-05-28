package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 快问快答答题记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizRecordDTO implements Serializable {

    private String roomId;

    private long questionId;

    private String question;

    private List<String> options;

    private long createdAt;

    private List<QuickQuizAnswerViewDTO> answers;

}
