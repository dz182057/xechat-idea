package cn.xeblog.server.game.quickquiz;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 快问快答 Mapper。
 */
public interface QuickQuizMapper {

    void upsertQuestion(QuickQuizQuestion question);

    void deactivateAllQuestions();

    List<QuickQuizQuestion> listActiveQuestions();

    QuickQuizQuestion randomAvailableQuestion(@Param("usedQuestionIds") List<Long> usedQuestionIds);

    int countAvailableQuestions(@Param("usedQuestionIds") List<Long> usedQuestionIds);

    void insertRecord(QuickQuizRecord record);

    List<QuickQuizRecord> listRecordsByPlayer(@Param("playerKey") String playerKey);

    List<QuickQuizRecord> listAllRecords();

}
