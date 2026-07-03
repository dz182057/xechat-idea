package cn.xeblog.commons.entity.react.result;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员查看用户狗狗之家资源概览。
 *
 * @author dz
 * @date 2026/7/3
 */
@Data
@NoArgsConstructor
public class AdminPetResourceOverviewDTO {

    private Long accountId;
    private String account;
    private String nickname;
    private String accountStatus;
    private Long createdAt;
    private Long lastLoginAt;

    private boolean hasPetHome;
    private Integer bones;
    private Integer food;
    private Integer makeupCards;
    private Integer dogSlots;
    private Integer energy;
    private Integer energyLimit;
    private String energyDate;
    private String companionDogId;
    private Long petUpdatedAt;

    private Integer dogCount;
    private Integer exploringDogCount;
    private String dogSummary;

    private Integer itemKindCount;
    private Integer itemTotalCount;
    private String itemSummary;

    private Integer skinKindCount;
    private Integer skinTotalCount;
    private String skinSummary;

    private Integer collectionKindCount;
    private Integer collectionTotalCount;
    private String collectionSummary;

}
