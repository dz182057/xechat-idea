package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_items 表 Mapper。
 */
public interface PetItemMapper {

    PetItemRecord findByAccountIdAndItemId(@Param("accountId") long accountId,
                                           @Param("itemId") String itemId);

    List<PetItemRecord> listPositiveByAccountId(@Param("accountId") long accountId);

    int addItemIfUnderLimit(@Param("accountId") long accountId,
                            @Param("itemId") String itemId,
                            @Param("quantity") int quantity,
                            @Param("maxCount") int maxCount,
                            @Param("updatedAt") long updatedAt);

    int decrementItemIfEnough(@Param("accountId") long accountId,
                              @Param("itemId") String itemId,
                              @Param("quantity") int quantity,
                              @Param("updatedAt") long updatedAt);

}
