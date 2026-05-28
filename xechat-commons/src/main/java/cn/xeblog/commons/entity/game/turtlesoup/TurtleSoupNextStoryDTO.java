package cn.xeblog.commons.entity.game.turtlesoup;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 海龟汤请求下一题。
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurtleSoupNextStoryDTO extends GameDTO {

    public TurtleSoupNextStoryDTO(String roomId) {
        super(roomId, Game.TURTLE_SOUP);
    }

}
