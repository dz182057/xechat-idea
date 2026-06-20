package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_item_ledger 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetItemLedgerRecord {

    private String id;
    private long accountId;
    private String itemId;
    private int quantity;
    private String direction;
    private String source;
    private String sourceRef;
    private String metadataJson;
    private long createdAt;

}
