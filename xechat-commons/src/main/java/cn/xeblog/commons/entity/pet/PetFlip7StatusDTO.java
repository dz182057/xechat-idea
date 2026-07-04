package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 翻转7每日状态和费用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7StatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverDate;

    private int dailyFreeLimit;

    private int dailyFreeUsed;

    private int dailyFreeRemaining;

    private int paidPlayCost;

    private int deckRemaining;

    private int discardCount;

    private List<PetFlip7CardCountDTO> totalCards;

    private List<PetFlip7CardCountDTO> remainingCards;

    private PetFlip7RoundDTO activeRound;

}
