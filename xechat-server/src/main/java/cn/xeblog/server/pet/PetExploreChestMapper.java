package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_explore_chests 表 Mapper。
 */
public interface PetExploreChestMapper {

    void insert(PetExploreChestRecord chest);

    PetExploreChestRecord findAvailableByIdAndAccountId(@Param("id") String id,
                                                        @Param("accountId") long accountId);

    List<PetExploreChestRecord> listAvailableByAccountId(@Param("accountId") long accountId);

    int countAvailableByAccountIdAndChestItemId(@Param("accountId") long accountId,
                                                @Param("chestItemId") String chestItemId);

    int markOpened(@Param("id") String id,
                   @Param("accountId") long accountId,
                   @Param("openedAt") long openedAt);

}
