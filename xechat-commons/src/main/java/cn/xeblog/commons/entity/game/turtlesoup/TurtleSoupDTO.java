package cn.xeblog.commons.entity.game.turtlesoup;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 海龟汤游戏事件。
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurtleSoupDTO extends GameDTO {

    private Event event;

    private long storyId;

    private int roundNo;

    private String hostId;

    private String hostName;

    private String guesserId;

    private String guesserName;

    private String title;

    private String surface;

    private String bottom;

    private String difficulty;

    private String tags;

    private String content;

    private String answer;

    private GuessResult guessResult;

    private int guessLimit;

    private int guessUsed;

    private boolean finished;

    public TurtleSoupDTO(String roomId) {
        super(roomId, Game.TURTLE_SOUP);
    }

    public enum Event {
        PREVIEW_STORY,
        CONFIRM_STORY,
        START_ROUND,
        QUESTION,
        ANSWER,
        GUESS,
        JUDGE,
        REVEAL
    }

    public enum GuessResult {
        CORRECT,
        PARTIAL,
        WRONG
    }

}
