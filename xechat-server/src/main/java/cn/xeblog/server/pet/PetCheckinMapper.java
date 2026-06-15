package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_checkins 表 Mapper。
 */
public interface PetCheckinMapper {

    PetCheckinRecord findByAccountIdAndDate(@Param("accountId") long accountId,
                                            @Param("checkinDate") String checkinDate);

    int countByAccountId(@Param("accountId") long accountId);

    List<String> listDatesByAccountIdAndMonthPrefix(@Param("accountId") long accountId,
                                                    @Param("monthPrefix") String monthPrefix);

    void insert(PetCheckinRecord checkin);

}
