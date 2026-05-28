package cn.xeblog.server.game.quickquiz;

import lombok.Builder;
import lombok.Data;

/**
 * quick_quiz_questions 表记录。
 */
@Data
@Builder
public class QuickQuizQuestion {

    private long id;

    private String question;

    private String optionsJson;

    private int sortOrder;

    private int active;

    private long createdAt;

    private long updatedAt;

}
