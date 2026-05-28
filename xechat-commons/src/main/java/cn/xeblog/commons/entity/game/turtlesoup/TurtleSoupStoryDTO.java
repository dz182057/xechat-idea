package cn.xeblog.commons.entity.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 海龟汤题目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupStoryDTO implements Serializable {

    private long id;

    /**
     * 标题，便于管理员识别题目。
     */
    private String title;

    /**
     * 汤面，玩家可见。
     */
    private String surface;

    /**
     * 汤底，仅主持人和结算后可见。
     */
    private String bottom;

    /**
     * 难度，后续可用于按难度随机。
     */
    private String difficulty;

    /**
     * 逗号分隔标签。
     */
    private String tags;

}
