package cn.xeblog.server.pet;

import cn.xeblog.commons.enums.Game;
import org.junit.Assert;
import org.junit.Test;

public class PetItemDefinitionsTest {

    @Test
    public void definitionsExposeV5PoolsPricesAndInteractionRewards() {
        Assert.assertTrue(PetItemDefinitions.shopNormalItemIds().contains("item_hint"));
        Assert.assertFalse(PetItemDefinitions.shopNormalItemIds().contains("item_turtle_probe"));
        Assert.assertTrue(PetItemDefinitions.luckyBagRareItemIds().contains("item_turtle_probe"));
        Assert.assertTrue(PetItemDefinitions.luckyBagEpicItemIds().contains("item_lucky_day"));

        Assert.assertEquals(Integer.valueOf(20), PetItemDefinitions.sellItemPrices().get("item_hint"));
        Assert.assertEquals(Integer.valueOf(80), PetItemDefinitions.sellItemPrices().get("item_turtle_probe"));
        Assert.assertEquals(Integer.valueOf(200), PetItemDefinitions.sellItemPrices().get("item_lucky_day"));

        Assert.assertEquals(Integer.valueOf(40),
                PetItemDefinitions.interactionRewardBones().get("item_battle_direct_hit"));
        Assert.assertFalse(PetItemDefinitions.interactionRewardBones().containsKey("item_hint"));
    }

    @Test
    public void definitionsExposeGameSlotsAndModeLimits() {
        Assert.assertTrue(PetItemDefinitions.isPlayItem(Game.MINESWEEPER, "item_mine_shield"));
        Assert.assertTrue(PetItemDefinitions.isInteractionItem(Game.TACIT_QUIZ, "item_sync_prophecy"));
        Assert.assertFalse(PetItemDefinitions.isInteractionItem(Game.TACIT_QUIZ, "item_hint"));

        Assert.assertEquals("item_battle_echo", PetItemDefinitions.firstCommonPlayItem(Game.DOG_BATTLE));
        Assert.assertNull(PetItemDefinitions.firstCommonPlayItem(Game.GOBANG));

        Assert.assertFalse(PetItemDefinitions.byId("item_hint").isFormalModeAllowed());
        Assert.assertTrue(PetItemDefinitions.byId("item_prophecy").isFormalModeAllowed());
    }
}
