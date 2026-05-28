package cn.xeblog.commons.entity.game.turtlesoup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员保存海龟汤题库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSaveTurtleSoupStoriesDTO implements Serializable {

    private List<TurtleSoupStoryDTO> stories;

}
