package cn.xeblog.server.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 海龟汤历史记录行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupRecord {

    private long id;

    private String roomId;

    private long storyId;

    private int roundNo;

    private String hostKey;

    private String hostName;

    private String guesserKey;

    private String guesserName;

    private int guessLimit;

    private int guessUsed;

    private String result;

    private String qaJson;

    private long startedAt;

    private long endedAt;

    private String surface;

    private String bottom;

    private String title;

    private String difficulty;

    private String tags;

}
