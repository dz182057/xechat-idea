package cn.xeblog.commons.entity.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 海龟汤单条问答/猜底记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupLogItemDTO implements Serializable {

    private String type;

    private String userId;

    private String username;

    private String content;

    private String answer;

    private String result;

    private long time;

}
