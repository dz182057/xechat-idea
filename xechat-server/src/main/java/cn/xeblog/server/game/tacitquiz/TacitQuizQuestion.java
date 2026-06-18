package cn.xeblog.server.game.tacitquiz;

import lombok.Builder;
import lombok.Data;

/**
 * tacit_quiz_questions 表记录。
 */
@Data
@Builder
public class TacitQuizQuestion {

    private long id;

    private String question;

    private String optionsJson;

    private int sortOrder;

    private int active;

    private long createdAt;

    private long updatedAt;

}
