package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_daily_saying_assignment 表 Mapper。
 */
public interface PetDailySayingAssignmentMapper {

    void insert(PetDailySayingAssignmentRecord record);

    PetDailySayingAssignmentRecord findUnread(@Param("accountId") long accountId);

    PetDailySayingAssignmentRecord findReadOnDate(@Param("accountId") long accountId,
                                                  @Param("readServerDate") String readServerDate);

    PetDailySayingAssignmentRecord findByIdAndAccount(@Param("assignmentId") String assignmentId,
                                                      @Param("accountId") long accountId);

    int markRead(@Param("assignmentId") String assignmentId,
                 @Param("accountId") long accountId,
                 @Param("readAt") long readAt,
                 @Param("readServerDate") String readServerDate,
                 @Param("greetingRewardApplied") boolean greetingRewardApplied,
                 @Param("greetingIntimacyDelta") int greetingIntimacyDelta);

    List<String> listRecentContentIds(@Param("accountId") long accountId,
                                      @Param("limit") int limit);

    List<String> listRecentCategories(@Param("accountId") long accountId,
                                      @Param("limit") int limit);

    List<PetDailySayingAssignmentRecord> listRecentRead(@Param("accountId") long accountId,
                                                        @Param("limit") int limit);

}
