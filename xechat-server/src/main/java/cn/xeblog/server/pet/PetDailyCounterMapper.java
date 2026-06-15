package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

/**
 * pet_daily_counters 表 Mapper。
 */
public interface PetDailyCounterMapper {

    int incrementIfUnderLimit(@Param("accountId") long accountId,
                              @Param("counterDate") String counterDate,
                              @Param("counter") String counter,
                              @Param("limit") int limit,
                              @Param("updatedAt") long updatedAt);

    int incrementByIfUnderLimit(@Param("accountId") long accountId,
                                @Param("counterDate") String counterDate,
                                @Param("counter") String counter,
                                @Param("amount") int amount,
                                @Param("limit") int limit,
                                @Param("updatedAt") long updatedAt);

}
