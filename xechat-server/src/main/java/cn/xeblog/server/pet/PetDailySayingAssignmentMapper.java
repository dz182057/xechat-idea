package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentDTO;
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

    PetDailySayingAssignmentRecord findByAssignmentId(@Param("assignmentId") String assignmentId);

    AdminPetDailySayingAssignmentDTO findAdminByAssignmentId(@Param("assignmentId") String assignmentId);

    int markRead(@Param("accountId") long accountId,
                 @Param("assignmentId") String assignmentId,
                 @Param("readAt") long readAt,
                 @Param("readServerDate") String readServerDate,
                 @Param("greetingRewardApplied") boolean greetingRewardApplied,
                 @Param("greetingIntimacyDelta") int greetingIntimacyDelta);

    int reassign(@Param("assignmentId") String assignmentId,
                 @Param("accountId") long accountId,
                 @Param("dogId") String dogId,
                 @Param("dogNameSnapshot") String dogNameSnapshot,
                 @Param("dogAvatarSnapshot") String dogAvatarSnapshot,
                 @Param("contentId") String contentId,
                 @Param("assignedAt") long assignedAt,
                 @Param("contentVersion") String contentVersion);

    int countAdminByDate(@Param("assignedServerDate") String assignedServerDate,
                         @Param("keyword") String keyword,
                         @Param("status") String status);

    List<AdminPetDailySayingAssignmentDTO> listAdminByDate(
            @Param("assignedServerDate") String assignedServerDate,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<PetDailySayingAssignmentRecord> listRecentRead(@Param("accountId") long accountId,
                                                        @Param("limit") int limit);

    List<String> listRecentAssignedContentIds(@Param("accountId") long accountId,
                                              @Param("limit") int limit);

    List<String> listAssignedPrimaryTextsByAccount(@Param("accountId") long accountId);

    List<String> listRecentReadCategories(@Param("accountId") long accountId,
                                          @Param("limit") int limit);

}
