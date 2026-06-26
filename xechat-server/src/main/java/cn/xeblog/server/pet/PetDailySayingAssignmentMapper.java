package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_daily_saying_assignments 表 Mapper。
 */
public interface PetDailySayingAssignmentMapper {

    int insert(PetDailySayingAssignmentRecord record);

    PetDailySayingAssignmentRecord findUnread(@Param("accountId") long accountId);

    PetDailySayingAssignmentRecord findReadOnDate(@Param("accountId") long accountId,
                                                  @Param("readServerDate") String readServerDate);

    PetDailySayingAssignmentRecord findById(@Param("accountId") long accountId,
                                            @Param("assignmentId") String assignmentId);

    int markRead(@Param("accountId") long accountId,
                 @Param("assignmentId") String assignmentId,
                 @Param("readAt") long readAt,
                 @Param("readServerDate") String readServerDate,
                 @Param("greetingRewardApplied") boolean greetingRewardApplied,
                 @Param("greetingIntimacyDelta") int greetingIntimacyDelta);

    List<PetDailySayingAssignmentRecord> listRecentRead(@Param("accountId") long accountId,
                                                        @Param("limit") int limit);

    List<String> listRecentAssignedContentIds(@Param("accountId") long accountId,
                                              @Param("limit") int limit);

    List<String> listAssignedPrimaryTextsByAccount(@Param("accountId") long accountId);

    List<String> listRecentReadCategories(@Param("accountId") long accountId,
                                          @Param("limit") int limit);

}
