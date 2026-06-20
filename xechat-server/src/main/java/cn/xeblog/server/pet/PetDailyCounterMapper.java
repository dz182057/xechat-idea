package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_daily_counters 表 Mapper。
 */
public interface PetDailyCounterMapper {

    Integer findValue(@Param("accountId") long accountId,
                      @Param("counterDate") String counterDate,
                      @Param("counter") String counter);

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

    List<String> listCountersByPrefix(@Param("accountId") long accountId,
                                      @Param("counterDate") String counterDate,
                                      @Param("prefix") String prefix);

    int deleteCounter(@Param("accountId") long accountId,
                      @Param("counterDate") String counterDate,
                      @Param("counter") String counter);

}
