package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

/**
 * pet_flip7_states 表 Mapper。
 */
public interface PetFlip7StateMapper {

    PetFlip7StateRecord findByAccountId(@Param("accountId") long accountId);

    int upsert(PetFlip7StateRecord record);

}
