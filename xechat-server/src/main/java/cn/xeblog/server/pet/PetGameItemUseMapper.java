package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

public interface PetGameItemUseMapper {

    int insert(PetGameItemUseRecord record);

    PetGameItemUseRecord findLatestReserved(@Param("gameId") String gameId,
                                            @Param("accountId") long accountId,
                                            @Param("itemId") String itemId,
                                            @Param("slot") String slot);

    int markSettled(@Param("id") String id,
                    @Param("status") String status,
                    @Param("rewardBones") int rewardBones,
                    @Param("settledAt") long settledAt);
}
