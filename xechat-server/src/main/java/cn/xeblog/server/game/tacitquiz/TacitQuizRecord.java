package cn.xeblog.server.game.tacitquiz;

import lombok.Builder;
import lombok.Data;

/**
 * tacit_quiz_records 表记录。
 */
@Data
@Builder
public class TacitQuizRecord {

    private long id;

    private String roomId;

    private long questionId;

    private String playerKey;

    private String username;

    private int choiceIndex;

    private String choiceText;

    private long createdAt;

    private String question;

    private String optionsJson;

}
