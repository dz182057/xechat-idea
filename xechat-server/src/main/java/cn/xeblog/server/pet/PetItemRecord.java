package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_items 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetItemRecord {

    private long accountId;
    private String itemId;
    private int count;
    private long updatedAt;

}
