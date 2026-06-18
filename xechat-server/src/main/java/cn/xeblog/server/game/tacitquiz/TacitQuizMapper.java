package cn.xeblog.server.game.tacitquiz;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 默契问答 Mapper。
 */
public interface TacitQuizMapper {

    void upsertQuestion(TacitQuizQuestion question);

    void deactivateAllQuestions();

    List<TacitQuizQuestion> listActiveQuestions();

    TacitQuizQuestion randomAvailableQuestion(@Param("playerAKey") String playerAKey,
                                              @Param("playerBKey") String playerBKey,
                                              @Param("usedQuestionIds") List<Long> usedQuestionIds);

    int countAvailableQuestions(@Param("playerAKey") String playerAKey,
                                @Param("playerBKey") String playerBKey,
                                @Param("usedQuestionIds") List<Long> usedQuestionIds);

    void insertRecord(TacitQuizRecord record);

    List<TacitQuizRecord> listRecordsByPlayer(@Param("playerKey") String playerKey);

    List<TacitQuizRecord> listAllRecords();

}
