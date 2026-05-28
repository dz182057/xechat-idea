package cn.xeblog.server.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 海龟汤题库行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupStory {

    private long id;

    private String title;

    private String surface;

    private String bottom;

    private String difficulty;

    private String tags;

    private int sortOrder;

    private int active;

    private long createdAt;

    private long updatedAt;

}
