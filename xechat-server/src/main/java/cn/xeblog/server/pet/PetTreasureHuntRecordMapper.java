package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_treasure_hunt_records 表 Mapper。
 */
public interface PetTreasureHuntRecordMapper {

    void insert(PetTreasureHuntRecord record);

    List<PetTreasureHuntRecord> listRecentByAccountId(@Param("accountId") long accountId,
                                                       @Param("limit") int limit);

    void deleteOlderThanRecentLimit(@Param("accountId") long accountId,
                                    @Param("limit") int limit);

}
