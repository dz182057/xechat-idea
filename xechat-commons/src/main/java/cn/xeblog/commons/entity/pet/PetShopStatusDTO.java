package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 狗狗宇宙商店个人货架状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetShopStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String rareItemId;

    private List<String> normalItemIds;

    private long currentPeriodStartAt;

    private long nextFreeRefreshAt;

    private int paidRefreshesUsed;

    private int maxPaidRefreshes;

    private List<Integer> paidRefreshCosts;

    private Integer nextPaidRefreshCost;

}
