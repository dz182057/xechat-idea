package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_item_ledger 表 Mapper。
 */
public interface PetItemLedgerMapper {

    void insert(PetItemLedgerRecord record);

    List<String> listGainedItemIds(@Param("accountId") long accountId);

}
