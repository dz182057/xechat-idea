package cn.xeblog.server.game.drawguess;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * draw_guess_words 表 Mapper。
 */
public interface DrawGuessWordMapper {

    void insert(DrawGuessWord word);

    void deleteAll();

    List<DrawGuessWord> listAll();

    DrawGuessWord randomOne();

    DrawGuessWord findByWord(@Param("word") String word);

}
