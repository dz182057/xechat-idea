package cn.xeblog.server.pet;

import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.PetItemDefinition.ReleaseStage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PetItemDefinitionsTest {

    @Test
    public void definitionsExposeV5PoolsPricesAndInteractionRewards() {
        Assert.assertEquals(Arrays.asList(
                "item_mine_mark",
                "item_mine_safe_ping",
                "item_draw_advance_hint",
                "item_draw_pattern",
                "item_draw_overlap",
                "item_sync_prophecy",
                "item_quiz_score_pad",
                "item_quiz_duel",
                "item_gomoku_prediction",
                "item_battle_echo",
                "item_battle_direct_hit",
                "item_prophecy"
        ), PetItemDefinitions.luckyBagNormalItemIds());
        Assert.assertTrue(PetItemDefinitions.shopNormalItemIds().contains("item_draw_advance_hint"));
        Assert.assertFalse(PetItemDefinitions.shopNormalItemIds().contains("item_turtle_probe"));
        Assert.assertEquals(Arrays.asList(
                "item_mine_shield",
                "item_mine_detector",
                "item_mine_counter",
                "item_draw_reveal_char",
                "item_sync_perspective",
                "item_quiz_wrong_option",
                "item_gomoku_guard",
                "item_turtle_probe",
                "item_battle_pebble",
                "item_battle_airbag"
        ), PetItemDefinitions.luckyBagRareItemIds());
        Assert.assertTrue(PetItemDefinitions.luckyBagRareItemIds().contains("item_turtle_probe"));
        Assert.assertFalse(PetItemDefinitions.luckyBagRareItemIds().contains("item_race_knee"));
        Assert.assertTrue(PetItemDefinitions.luckyBagEpicItemIds().contains("item_lucky_day"));

        Assert.assertEquals(Integer.valueOf(20), PetItemDefinitions.sellItemPrices().get("item_draw_advance_hint"));
        Assert.assertEquals(Integer.valueOf(80), PetItemDefinitions.sellItemPrices().get("item_turtle_probe"));
        Assert.assertEquals(Integer.valueOf(80), PetItemDefinitions.sellItemPrices().get("item_race_knee"));
        Assert.assertEquals(Integer.valueOf(200), PetItemDefinitions.sellItemPrices().get("item_lucky_day"));

        Assert.assertEquals(ReleaseStage.EXPANSION,
                PetItemDefinitions.byId("item_race_knee").getReleaseStage());

        Assert.assertEquals(Integer.valueOf(40),
                PetItemDefinitions.interactionRewardBones().get("item_battle_direct_hit"));
        Assert.assertFalse(PetItemDefinitions.interactionRewardBones().containsKey("item_draw_advance_hint"));
    }

    @Test
    public void expansionItemsShouldStayOutOfCorePurchaseAndDropPools() {
        List<String> expansionItemIds = Arrays.asList(
                "item_mine_share_marks",
                "item_mine_wrong_flag",
                "item_gomoku_review",
                "item_turtle_menu",
                "item_draw_replay",
                "item_sync_secret_question",
                "item_race_knee",
                "item_gift_pack"
        );

        for (String itemId : expansionItemIds) {
            Assert.assertEquals(itemId + " 应标记为扩展道具",
                    ReleaseStage.EXPANSION, PetItemDefinitions.byId(itemId).getReleaseStage());
            Assert.assertFalse(itemId + " 不应进入普通商店",
                    PetItemDefinitions.shopNormalItemIds().contains(itemId));
            Assert.assertFalse(itemId + " 不应进入福袋普通池",
                    PetItemDefinitions.luckyBagNormalItemIds().contains(itemId));
            Assert.assertFalse(itemId + " 不应进入福袋稀有池",
                    PetItemDefinitions.luckyBagRareItemIds().contains(itemId));
            Assert.assertFalse(itemId + " 不应进入福袋史诗池",
                    PetItemDefinitions.luckyBagEpicItemIds().contains(itemId));
            Assert.assertFalse(itemId + " 不应进入福袋总池",
                    PetItemDefinitions.luckyBagAllItemIds().contains(itemId));
        }
    }

    @Test
    public void definitionsExposeGameSlotsAndModeLimits() {
        Assert.assertTrue(PetItemDefinitions.isPlayItem(Game.MINESWEEPER, "item_mine_shield"));
        Assert.assertTrue(PetItemDefinitions.isInteractionItem(Game.TACIT_QUIZ, "item_sync_prophecy"));
        Assert.assertFalse(PetItemDefinitions.isInteractionItem(Game.TACIT_QUIZ, "item_draw_advance_hint"));

        Assert.assertEquals("item_battle_echo", PetItemDefinitions.firstCommonPlayItem(Game.DOG_BATTLE));
        Assert.assertNull(PetItemDefinitions.firstCommonPlayItem(Game.GOBANG));

        Assert.assertFalse(PetItemDefinitions.byId("item_draw_advance_hint").isFormalModeAllowed());
        Assert.assertTrue(PetItemDefinitions.byId("item_prophecy").isFormalModeAllowed());
    }
}
