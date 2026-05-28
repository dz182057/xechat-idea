package cn.xeblog.commons.entity.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 海龟汤历史记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupRecordDTO implements Serializable {

    private long id;

    private String roomId;

    private long storyId;

    private int roundNo;

    private String title;

    private String surface;

    private String bottom;

    private String difficulty;

    private String tags;

    private String hostKey;

    private String hostName;

    private String guesserKey;

    private String guesserName;

    private int guessLimit;

    private int guessUsed;

    private String result;

    private List<TurtleSoupLogItemDTO> logs;

    private long startedAt;

    private long endedAt;

}
