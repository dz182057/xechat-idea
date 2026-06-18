package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_collections 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetCollectionRecord {

    private long accountId;
    private String itemId;
    private int count;
    private boolean discovered;
    private long updatedAt;

}
