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
    private static final int LEGENDARY_SELL_PRICE = 500;
    private static final int RARE_SKIN_FRAGMENT_SELL_PRICE = 21;
    private static final int EPIC_SKIN_FRAGMENT_SELL_PRICE = 42;
    private static final int RARE_SKIN_SELL_PRICE = 210;
    private static final int EPIC_SKIN_SELL_PRICE = 420;
    private static final int LEGENDARY_SKIN_SELL_PRICE = 2000;
    public static final String ITEM_SKIN_TICKET = "item_skin_ticket";
    public static final String ITEM_RARE_SKIN_FRAGMENT = "item_rare_skin_fragment";
    public static final String ITEM_EPIC_SKIN_FRAGMENT = "item_epic_skin_fragment";
    private static final String ITEM_WILD_COMMON = "item_wild_common";
    private static final String ITEM_PARTY_EQUALIZER = "item_party_equalizer";
    private static final Set<String> TEMPORARILY_DISABLED_ITEM_IDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(
                    "item_battle_echo",
                    "item_battle_direct_hit",
                    "item_battle_pebble",
                    "item_battle_airbag",
                    "item_race_knee"
            )));
    private static final Set<Game> TEMPORARILY_DISABLED_GAMES = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(Game.DOG_BATTLE, Game.DOG_RACE)));

    private static final List<String> RARE_SKIN_ITEM_IDS = Collections.unmodifiableList(List.of(
            "item_minesweeper_skin_toy"
    ));
    private static final List<String> EPIC_SKIN_ITEM_IDS = Collections.unmodifiableList(List.of(
            "item_minesweeper_skin_ink_wash",
            "item_gomoku_skin_magic",
            "item_gomoku_skin_starry",
            "item_gomoku_skin_fairy",
            "item_gomoku_skin_ink",
            "item_gomoku_skin_toy",
            "item_gomoku_skin_deepsea"
    ));
    private static final List<String> LEGENDARY_SKIN_ITEM_IDS = Collections.emptyList();
    private static final List<String> SKIN_ITEM_IDS = combineSkinItemIds();
    private static final List<PetItemDefinition> DEFINITIONS = createDefinitions();
    private static final Map<String, PetItemDefinition> DEFINITIONS_BY_ID = indexById(DEFINITIONS);
    private static final List<String> LUCKY_BAG_NORMAL_ITEM_IDS = itemIdsByRarity(Rarity.COMMON);
    private static final List<String> LUCKY_BAG_RARE_ITEM_IDS = itemIdsByRarity(Rarity.RARE);
    private static final List<String> LUCKY_BAG_EPIC_ITEM_IDS = itemIdsByRarity(Rarity.EPIC);
    private static final List<String> LUCKY_BAG_ITEM_IDS = luckyBagItemIds();
    private static final List<String> DAILY_SKIN_SHOP_ITEM_IDS = SKIN_ITEM_IDS;
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

    public static List<String> dailySkinShopItemIds() {
        return DAILY_SKIN_SHOP_ITEM_IDS;
    }

    public static List<String> skinItemIds() {
        return SKIN_ITEM_IDS;
    }

    public static List<String> rareSkinItemIds() {
        return RARE_SKIN_ITEM_IDS;
    }

    public static List<String> epicSkinItemIds() {
        return EPIC_SKIN_ITEM_IDS;
    }

    public static List<String> legendarySkinItemIds() {
        return LEGENDARY_SKIN_ITEM_IDS;
    }

    public static boolean isDailySkinShopItem(String itemId) {
        return DAILY_SKIN_SHOP_ITEM_IDS.contains(trimToNull(itemId));
    }

    public static boolean isSkinItem(String itemId) {
        return SKIN_ITEM_IDS.contains(trimToNull(itemId));
    }

    public static Map<String, Integer> sellItemPrices() {
        return SELL_ITEM_PRICES;
    }

    public static Map<String, Integer> interactionRewardBones() {
        return INTERACTION_REWARD_BONES;
    }

    public static boolean isPlayItem(Game game, String itemId) {
        PetItemDefinition definition = byId(itemId);
        return !isTemporarilyDisabledGame(game)
                && !isTemporarilyDisabledItem(itemId)
                && definition != null
                && definition.getSlot() == Slot.PLAY
                && definition.getRelatedGames().contains(game);
    }

    public static boolean isInteractionItem(Game game, String itemId) {
        PetItemDefinition definition = byId(itemId);
        return !isTemporarilyDisabledGame(game)
                && !isTemporarilyDisabledItem(itemId)
                && definition != null
                && definition.getSlot() == Slot.INTERACTION
                && definition.getRelatedGames().contains(game);
    }

    public static boolean isCarryItem(Game game, String itemId) {
        PetItemDefinition definition = byId(itemId);
        return !isTemporarilyDisabledGame(game)
                && !isTemporarilyDisabledItem(itemId)
                && definition != null
                && definition.getSlot() != Slot.UTILITY
                && definition.getRelatedGames().contains(game);
    }

    public static String firstCommonPlayItem(Game game) {
        if (game == null || isTemporarilyDisabledGame(game)) {
            return null;
        }
        for (PetItemDefinition definition : DEFINITIONS) {
            if (!isTemporarilyDisabledItem(definition.getItemId())
                    && definition.getSlot() == Slot.PLAY
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

    public static boolean isTemporarilyDisabledItem(String itemId) {
        return TEMPORARILY_DISABLED_ITEM_IDS.contains(trimToNull(itemId));
    }

    private static boolean isTemporarilyDisabledGame(Game game) {
        return TEMPORARILY_DISABLED_GAMES.contains(game);
    }

    private static List<String> combineSkinItemIds() {
        List<String> itemIds = new ArrayList<>();
        itemIds.addAll(RARE_SKIN_ITEM_IDS);
        itemIds.addAll(EPIC_SKIN_ITEM_IDS);
        itemIds.addAll(LEGENDARY_SKIN_ITEM_IDS);
        return Collections.unmodifiableList(itemIds);
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
        utility(items, "item_minesweeper_skin_ink_wash", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.MINESWEEPER);
        utility(items, "item_minesweeper_skin_toy", Rarity.RARE, ReleaseStage.CORE,
                RARE_SKIN_SELL_PRICE, Game.MINESWEEPER);
        utility(items, "item_gomoku_skin_magic", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);
        utility(items, "item_gomoku_skin_starry", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);
        utility(items, "item_gomoku_skin_fairy", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);
        utility(items, "item_gomoku_skin_ink", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);
        utility(items, "item_gomoku_skin_toy", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);
        utility(items, "item_gomoku_skin_deepsea", Rarity.EPIC, ReleaseStage.CORE,
                EPIC_SKIN_SELL_PRICE, Game.GOBANG);

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
        play(items, "item_gomoku_finisher", Rarity.RARE, false, Game.GOBANG);
        play(items, "item_turtle_probe", Rarity.RARE, false, Game.TURTLE_SOUP);
        play(items, "item_battle_pebble", Rarity.RARE, false, Game.DOG_BATTLE);
        play(items, "item_battle_airbag", Rarity.RARE, false, Game.DOG_BATTLE);
        utility(items, "item_race_knee", Rarity.RARE, ReleaseStage.EXPANSION, Game.DOG_RACE);

        utility(items, ITEM_WILD_COMMON, Rarity.EPIC);
        utility(items, ITEM_PARTY_EQUALIZER, Rarity.EPIC);
        utility(items, "item_gift_pack", Rarity.EPIC, ReleaseStage.EXPANSION);
        utility(items, ITEM_SKIN_TICKET, Rarity.EPIC, ReleaseStage.EXPANSION, COMMON_SELL_PRICE);
        utility(items, ITEM_RARE_SKIN_FRAGMENT, Rarity.RARE, ReleaseStage.EXPANSION,
                RARE_SKIN_FRAGMENT_SELL_PRICE);
        utility(items, ITEM_EPIC_SKIN_FRAGMENT, Rarity.EPIC, ReleaseStage.EXPANSION,
                EPIC_SKIN_FRAGMENT_SELL_PRICE);
        utility(items, "item_express", Rarity.EPIC);
        utility(items, "item_lucky_day", Rarity.EPIC);
        play(items, "item_gomoku_oracle", Rarity.LEGENDARY, false, Game.GOBANG);
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
        utility(items, itemId, rarity, releaseStage, sellPrice(rarity), games);
    }

    private static void utility(List<PetItemDefinition> items, String itemId, Rarity rarity,
                                ReleaseStage releaseStage, int sellPrice, Game... games) {
        items.add(new PetItemDefinition(itemId, rarity, releaseStage, Slot.UTILITY, sellPrice,
                null, true, false, games));
    }

    private static int sellPrice(Rarity rarity) {
        if (rarity == Rarity.RARE) {
            return RARE_SELL_PRICE;
        }
        if (rarity == Rarity.EPIC) {
            return EPIC_SELL_PRICE;
        }
        if (rarity == Rarity.LEGENDARY) {
            return LEGENDARY_SELL_PRICE;
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
            if (!isTemporarilyDisabledItem(definition.getItemId())
                    && !SKIN_ITEM_IDS.contains(definition.getItemId())
                    && definition.getRarity() == rarity
                    && definition.getReleaseStage() == ReleaseStage.CORE) {
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
            if (!isTemporarilyDisabledItem(definition.getItemId())
                    && definition.getInteractionRewardBones() != null) {
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
