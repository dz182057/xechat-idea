package cn.xeblog.server.pet;

import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.PetItemDefinition.Rarity;
import cn.xeblog.server.pet.PetItemDefinition.ReleaseStage;
import cn.xeblog.server.pet.PetItemDefinition.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PetItemDefinitions {

    private static final int COMMON_SELL_PRICE = 20;
    private static final int RARE_SELL_PRICE = 80;
    private static final int EPIC_SELL_PRICE = 200;
    private static final String ITEM_WILD_COMMON = "item_wild_common";
    private static final String ITEM_PARTY_EQUALIZER = "item_party_equalizer";

    private static final List<PetItemDefinition> DEFINITIONS = createDefinitions();
    private static final Map<String, PetItemDefinition> DEFINITIONS_BY_ID = indexById(DEFINITIONS);
    private static final List<String> LUCKY_BAG_NORMAL_ITEM_IDS = itemIdsByRarity(Rarity.COMMON);
    private static final List<String> LUCKY_BAG_RARE_ITEM_IDS = itemIdsByRarity(Rarity.RARE);
    private static final List<String> LUCKY_BAG_EPIC_ITEM_IDS = itemIdsByRarity(Rarity.EPIC);
    private static final List<String> LUCKY_BAG_ITEM_IDS = luckyBagItemIds();
    private static final Set<String> SHOP_NORMAL_ITEM_IDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(LUCKY_BAG_NORMAL_ITEM_IDS));
    private static final Map<String, Integer> SELL_ITEM_PRICES = sellPrices();
    private static final Map<String, Integer> INTERACTION_REWARD_BONES = interactionRewards();

    private PetItemDefinitions() {
    }

    public static PetItemDefinition byId(String itemId) {
        return DEFINITIONS_BY_ID.get(trimToNull(itemId));
    }

    public static List<String> luckyBagNormalItemIds() {
        return LUCKY_BAG_NORMAL_ITEM_IDS;
    }

    public static List<String> luckyBagRareItemIds() {
        return LUCKY_BAG_RARE_ITEM_IDS;
    }

    public static List<String> luckyBagEpicItemIds() {
        return LUCKY_BAG_EPIC_ITEM_IDS;
    }

    public static List<String> luckyBagAllItemIds() {
        return LUCKY_BAG_ITEM_IDS;
    }

    public static Set<String> shopNormalItemIds() {
        return SHOP_NORMAL_ITEM_IDS;
    }

    public static Map<String, Integer> sellItemPrices() {
        return SELL_ITEM_PRICES;
    }

    public static Map<String, Integer> interactionRewardBones() {
        return INTERACTION_REWARD_BONES;
    }

    public static boolean isPlayItem(Game game, String itemId) {
        PetItemDefinition definition = byId(itemId);
        return definition != null
                && definition.getSlot() == Slot.PLAY
                && definition.getRelatedGames().contains(game);
    }

    public static boolean isInteractionItem(Game game, String itemId) {
        PetItemDefinition definition = byId(itemId);
        return definition != null
                && definition.getSlot() == Slot.INTERACTION
                && definition.getRelatedGames().contains(game);
    }

    public static String firstCommonPlayItem(Game game) {
        if (game == null) {
            return null;
        }
        for (PetItemDefinition definition : DEFINITIONS) {
            if (definition.getSlot() == Slot.PLAY
                    && definition.isCommonPlayTarget()
                    && definition.getRelatedGames().contains(game)) {
                return definition.getItemId();
            }
        }
        return null;
    }

    public static boolean isWildCommonItem(String itemId) {
        return ITEM_WILD_COMMON.equals(trimToNull(itemId));
    }

    public static boolean isPartyEqualizerItem(String itemId) {
        return ITEM_PARTY_EQUALIZER.equals(trimToNull(itemId));
    }

    private static List<PetItemDefinition> createDefinitions() {
        List<PetItemDefinition> items = new ArrayList<>();
        play(items, "item_mine_mark", Rarity.COMMON, true, Game.MINESWEEPER);
        play(items, "item_mine_safe_ping", Rarity.COMMON, true, Game.MINESWEEPER);
        play(items, "item_draw_advance_hint", Rarity.COMMON, true, Game.DRAW_GUESS);
        play(items, "item_draw_pattern", Rarity.COMMON, true, Game.DRAW_GUESS);
        play(items, "item_draw_overlap", Rarity.COMMON, true, Game.DRAW_GUESS);
        interaction(items, "item_sync_prophecy", Rarity.COMMON, 20, Game.TACIT_QUIZ);
        play(items, "item_quiz_score_pad", Rarity.COMMON, true, Game.QUICK_QUIZ);
        interaction(items, "item_quiz_duel", Rarity.COMMON, 30, Game.QUICK_QUIZ);
        interaction(items, "item_gomoku_prediction", Rarity.COMMON, 50, Game.GOBANG);
        play(items, "item_battle_echo", Rarity.COMMON, true, Game.DOG_BATTLE);
        interaction(items, "item_battle_direct_hit", Rarity.COMMON, 40, Game.DOG_BATTLE);
        interaction(items, "item_prophecy", Rarity.COMMON, 50, Game.QUICK_QUIZ, Game.GOBANG, Game.DOG_BATTLE);
        interaction(items, "item_mine_share_marks", Rarity.COMMON, ReleaseStage.EXPANSION, null, Game.MINESWEEPER);
        interaction(items, "item_gomoku_review", Rarity.COMMON, ReleaseStage.EXPANSION, null, Game.GOBANG);
        interaction(items, "item_turtle_menu", Rarity.COMMON, ReleaseStage.EXPANSION, null, Game.TURTLE_SOUP);

        play(items, "item_mine_shield", Rarity.RARE, false, Game.MINESWEEPER);
        play(items, "item_mine_detector", Rarity.RARE, false, Game.MINESWEEPER);
        play(items, "item_mine_counter", Rarity.RARE, false, Game.MINESWEEPER);
        play(items, "item_mine_wrong_flag", Rarity.RARE, ReleaseStage.EXPANSION, false, Game.MINESWEEPER);
        play(items, "item_draw_reveal_char", Rarity.RARE, false, Game.DRAW_GUESS);
        play(items, "item_draw_replay", Rarity.RARE, ReleaseStage.EXPANSION, false, Game.DRAW_GUESS);
        interaction(items, "item_sync_perspective", Rarity.RARE, 30, Game.TACIT_QUIZ);
        interaction(items, "item_sync_secret_question", Rarity.RARE, ReleaseStage.EXPANSION, null, Game.TACIT_QUIZ);
        play(items, "item_quiz_wrong_option", Rarity.RARE, false, Game.QUICK_QUIZ);
        play(items, "item_gomoku_guard", Rarity.RARE, false, Game.GOBANG);
        play(items, "item_turtle_probe", Rarity.RARE, false, Game.TURTLE_SOUP);
        play(items, "item_battle_pebble", Rarity.RARE, false, Game.DOG_BATTLE);
        play(items, "item_battle_airbag", Rarity.RARE, false, Game.DOG_BATTLE);
        utility(items, "item_race_knee", Rarity.RARE, ReleaseStage.EXPANSION, Game.DOG_RACE);

        utility(items, ITEM_WILD_COMMON, Rarity.EPIC);
        utility(items, ITEM_PARTY_EQUALIZER, Rarity.EPIC);
        utility(items, "item_gift_pack", Rarity.EPIC, ReleaseStage.EXPANSION);
        utility(items, "item_express", Rarity.EPIC);
        utility(items, "item_lucky_day", Rarity.EPIC);
        return Collections.unmodifiableList(items);
    }

    private static void play(List<PetItemDefinition> items, String itemId, Rarity rarity,
                             boolean commonPlayTarget, Game... games) {
        play(items, itemId, rarity, ReleaseStage.CORE, commonPlayTarget, games);
    }

    private static void play(List<PetItemDefinition> items, String itemId, Rarity rarity,
                             ReleaseStage releaseStage, boolean commonPlayTarget, Game... games) {
        items.add(new PetItemDefinition(itemId, rarity, releaseStage, Slot.PLAY, sellPrice(rarity),
                null, false, commonPlayTarget, games));
    }

    private static void interaction(List<PetItemDefinition> items, String itemId, Rarity rarity,
                                    Integer rewardBones, Game... games) {
        interaction(items, itemId, rarity, ReleaseStage.CORE, rewardBones, games);
    }

    private static void interaction(List<PetItemDefinition> items, String itemId, Rarity rarity,
                                    ReleaseStage releaseStage, Integer rewardBones, Game... games) {
        items.add(new PetItemDefinition(itemId, rarity, releaseStage, Slot.INTERACTION, sellPrice(rarity),
                rewardBones, true, false, games));
    }

    private static void utility(List<PetItemDefinition> items, String itemId, Rarity rarity, Game... games) {
        utility(items, itemId, rarity, ReleaseStage.CORE, games);
    }

    private static void utility(List<PetItemDefinition> items, String itemId, Rarity rarity,
                                ReleaseStage releaseStage, Game... games) {
        items.add(new PetItemDefinition(itemId, rarity, releaseStage, Slot.UTILITY, sellPrice(rarity),
                null, true, false, games));
    }

    private static int sellPrice(Rarity rarity) {
        if (rarity == Rarity.RARE) {
            return RARE_SELL_PRICE;
        }
        if (rarity == Rarity.EPIC) {
            return EPIC_SELL_PRICE;
        }
        return COMMON_SELL_PRICE;
    }

    private static Map<String, PetItemDefinition> indexById(List<PetItemDefinition> definitions) {
        Map<String, PetItemDefinition> map = new LinkedHashMap<>();
        for (PetItemDefinition definition : definitions) {
            if (map.put(definition.getItemId(), definition) != null) {
                throw new IllegalStateException("重复的狗狗道具定义：" + definition.getItemId());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<String> itemIdsByRarity(Rarity rarity) {
        List<String> itemIds = new ArrayList<>();
        for (PetItemDefinition definition : DEFINITIONS) {
            if (definition.getRarity() == rarity && definition.getReleaseStage() == ReleaseStage.CORE) {
                itemIds.add(definition.getItemId());
            }
        }
        return Collections.unmodifiableList(itemIds);
    }

    private static List<String> luckyBagItemIds() {
        List<String> itemIds = new ArrayList<>();
        itemIds.addAll(LUCKY_BAG_NORMAL_ITEM_IDS);
        itemIds.addAll(LUCKY_BAG_RARE_ITEM_IDS);
        itemIds.addAll(LUCKY_BAG_EPIC_ITEM_IDS);
        return Collections.unmodifiableList(itemIds);
    }

    private static Map<String, Integer> sellPrices() {
        Map<String, Integer> prices = new LinkedHashMap<>();
        for (PetItemDefinition definition : DEFINITIONS) {
            prices.put(definition.getItemId(), definition.getSellPrice());
        }
        return Collections.unmodifiableMap(prices);
    }

    private static Map<String, Integer> interactionRewards() {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        for (PetItemDefinition definition : DEFINITIONS) {
            if (definition.getInteractionRewardBones() != null) {
                rewards.put(definition.getItemId(), definition.getInteractionRewardBones());
            }
        }
        return Collections.unmodifiableMap(rewards);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
