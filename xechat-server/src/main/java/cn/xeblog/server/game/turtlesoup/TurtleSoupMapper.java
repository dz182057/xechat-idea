package cn.xeblog.server.game.turtlesoup;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 海龟汤题库和记录 Mapper。
 */
public interface TurtleSoupMapper {

    void upsertStory(TurtleSoupStory story);

    void deactivateAllStories();

    List<TurtleSoupStory> listActiveStories();

    TurtleSoupStory randomAvailableStory(@Param("playerAKey") String playerAKey,
                                          @Param("playerBKey") String playerBKey,
                                          @Param("excludedStoryIds") List<Long> excludedStoryIds);

    void insertRecord(TurtleSoupRecord record);

    List<TurtleSoupRecord> listRecordsByPlayer(@Param("playerKey") String playerKey);

    List<TurtleSoupRecord> listAllRecords();

}
