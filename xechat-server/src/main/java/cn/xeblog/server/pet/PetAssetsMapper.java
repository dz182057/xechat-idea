package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

/**
 * pet_assets 表 Mapper。
 */
public interface PetAssetsMapper {

    PetAssetsRecord findByAccountId(@Param("accountId") long accountId);

    void insert(PetAssetsRecord assets);

    int update(PetAssetsRecord assets);

    int decrementFoodIfEnough(@Param("accountId") long accountId, @Param("updatedAt") long updatedAt);

    int decrementMakeupCardsIfEnough(@Param("accountId") long accountId, @Param("updatedAt") long updatedAt);

    int buySecondDogSlotIfAffordable(@Param("accountId") long accountId,
                                     @Param("price") int price,
                                     @Param("updatedAt") long updatedAt);

    int buyFoodIfAffordableAndUnderLimit(@Param("accountId") long accountId,
                                         @Param("quantity") int quantity,
                                         @Param("price") int price,
                                         @Param("maxFood") int maxFood,
                                         @Param("updatedAt") long updatedAt);

    int buyMakeupCardsIfAffordableAndUnderLimit(@Param("accountId") long accountId,
                                                @Param("quantity") int quantity,
                                                @Param("price") int price,
                                                @Param("maxMakeupCards") int maxMakeupCards,
                                                @Param("updatedAt") long updatedAt);

    int decrementBonesIfEnough(@Param("accountId") long accountId,
                               @Param("amount") int amount,
                               @Param("updatedAt") long updatedAt);

    int updateCompanionDogId(@Param("accountId") long accountId,
                             @Param("companionDogId") String companionDogId,
                             @Param("updatedAt") long updatedAt);

    int addBones(@Param("accountId") long accountId, @Param("amount") int amount, @Param("updatedAt") long updatedAt);

    int addFood(@Param("accountId") long accountId, @Param("amount") int amount, @Param("updatedAt") long updatedAt);

}
