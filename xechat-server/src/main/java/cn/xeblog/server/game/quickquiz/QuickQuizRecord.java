package cn.xeblog.server.game.quickquiz;

import lombok.Builder;
import lombok.Data;

/**
 * quick_quiz_records 表记录。
 */
@Data
@Builder
public class QuickQuizRecord {

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
