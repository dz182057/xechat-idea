package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.Assert;
import org.junit.Test;

public class PetGameItemRulesTest {

    @Test
    public void normalizeClearsTemporarilyDisabledDogBattleItems() {
        GamePlayerPetItemsDTO normalized = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_prophecy")
        );

        Assert.assertNull(normalized.getPetPlayItemId());
        Assert.assertNull(normalized.getPetInteractionItemId());
    }

    @Test
    public void normalizeClearsItemsFromWrongGameOrSlot() {
        GamePlayerPetItemsDTO wrongGame = PetGameItemRules.normalize(
                Game.GOBANG,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit")
        );
        GamePlayerPetItemsDTO wrongSlot = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_battle_direct_hit", "item_battle_echo")
        );

        Assert.assertNull(wrongGame.getPetPlayItemId());
        Assert.assertNull(wrongGame.getPetInteractionItemId());
        Assert.assertNull(wrongSlot.getPetPlayItemId());
        Assert.assertNull(wrongSlot.getPetInteractionItemId());
    }

    @Test
    public void normalizeKeepsCurrentNonRaceGameItems() {
        GamePlayerPetItemsDTO quickQuiz = PetGameItemRules.normalize(
                Game.QUICK_QUIZ,
                new GamePlayerPetItemsDTO("item_quiz_score_pad", "item_quiz_duel")
        );
        GamePlayerPetItemsDTO tacitQuiz = PetGameItemRules.normalize(
                Game.TACIT_QUIZ,
                new GamePlayerPetItemsDTO(null, "item_sync_prophecy")
        );

        Assert.assertEquals("item_quiz_score_pad", quickQuiz.getPetPlayItemId());
        Assert.assertEquals("item_quiz_duel", quickQuiz.getPetInteractionItemId());
        Assert.assertNull(tacitQuiz.getPetPlayItemId());
        Assert.assertEquals("item_sync_prophecy", tacitQuiz.getPetInteractionItemId());
    }

    @Test
    public void normalizeClearsLegalItemsWhenPlayerDoesNotOwnThem() {
        GamePlayerPetItemsDTO normalized = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit"),
                itemId -> "item_battle_echo".equals(itemId)
        );

        Assert.assertNull(normalized.getPetPlayItemId());
        Assert.assertNull(normalized.getPetInteractionItemId());
    }

    @Test
    public void normalizeClearsPlayItemsInFormalModeButKeepsInteractionItems() {
        GamePlayerPetItemsDTO normalized = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                "正式模式",
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit")
        );

        Assert.assertNull(normalized.getPetPlayItemId());
        Assert.assertNull(normalized.getPetInteractionItemId());
    }

    @Test
    public void normalizeConvertsWildCommonToCurrentGameNormalPlayItemOnly() {
        GamePlayerPetItemsDTO dogBattle = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_wild_common", null),
                itemId -> "item_wild_common".equals(itemId)
        );
        GamePlayerPetItemsDTO quickQuiz = PetGameItemRules.normalize(
                Game.QUICK_QUIZ,
                new GamePlayerPetItemsDTO("item_wild_common", null),
                itemId -> "item_wild_common".equals(itemId)
        );
        GamePlayerPetItemsDTO gobang = PetGameItemRules.normalize(
                Game.GOBANG,
                new GamePlayerPetItemsDTO("item_wild_common", "item_wild_common"),
                itemId -> "item_wild_common".equals(itemId)
        );

        Assert.assertNull(dogBattle.getPetPlayItemId());
        Assert.assertNull(dogBattle.getPetInteractionItemId());
        Assert.assertEquals("item_quiz_score_pad", quickQuiz.getPetPlayItemId());
        Assert.assertNull(quickQuiz.getPetInteractionItemId());
        Assert.assertNull(gobang.getPetPlayItemId());
        Assert.assertNull(gobang.getPetInteractionItemId());
    }

    @Test
    public void normalizeConvertsPartyEqualizerToCurrentGameNormalPlayItemOnly() {
        GamePlayerPetItemsDTO dogBattle = PetGameItemRules.normalize(
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_party_equalizer", null),
                itemId -> "item_party_equalizer".equals(itemId)
        );
        GamePlayerPetItemsDTO gobang = PetGameItemRules.normalize(
                Game.GOBANG,
                new GamePlayerPetItemsDTO("item_party_equalizer", "item_party_equalizer"),
                itemId -> "item_party_equalizer".equals(itemId)
        );

        Assert.assertNull(dogBattle.getPetPlayItemId());
        Assert.assertNull(dogBattle.getPetInteractionItemId());
        Assert.assertNull(gobang.getPetPlayItemId());
        Assert.assertNull(gobang.getPetInteractionItemId());
    }
}
