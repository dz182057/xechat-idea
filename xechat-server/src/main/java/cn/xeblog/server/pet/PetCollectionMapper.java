package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_collections 表 Mapper。
 */
public interface PetCollectionMapper {

    List<PetCollectionRecord> listByAccountId(@Param("accountId") long accountId);

    Integer findCount(@Param("accountId") long accountId,
                      @Param("itemId") String itemId);

    int countDiscovered(@Param("accountId") long accountId,
                        @Param("itemId") String itemId);

    int addCollection(@Param("accountId") long accountId,
                      @Param("itemId") String itemId,
                      @Param("updatedAt") long updatedAt);

    int decrementCollectionIfEnough(@Param("accountId") long accountId,
                                    @Param("itemId") String itemId,
                                    @Param("quantity") int quantity,
                                    @Param("updatedAt") long updatedAt);

}
