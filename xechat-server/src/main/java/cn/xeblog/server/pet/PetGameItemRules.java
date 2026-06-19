package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.enums.Game;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PetGameItemRules {

    private static final Map<Game, Set<String>> PLAY_ITEMS = new EnumMap<>(Game.class);
    private static final Map<Game, Set<String>> COMMON_PLAY_ITEMS = new EnumMap<>(Game.class);
    private static final Map<Game, Set<String>> INTERACTION_ITEMS = new EnumMap<>(Game.class);
    private static final String ITEM_WILD_COMMON = "item_wild_common";
    private static final String ITEM_PARTY_EQUALIZER = "item_party_equalizer";

    static {
        putPlay(Game.MINESWEEPER,
                "item_mine_mark",
                "item_mine_area",
                "item_mine_scout",
                "item_mine_shield",
                "item_mine_guard",
                "item_metal_detector");
        putPlay(Game.DRAW_GUESS,
                "item_hint",
                "item_peek",
                "item_time",
                "item_inspiration",
                "item_draw_inspiration",
                "item_draw_peek",
                "item_draw_time",
                "item_draw_replay");
        putPlay(Game.QUICK_QUIZ,
                "item_quiz_score_pad",
                "item_quiz_wrong_option");
        putPlay(Game.GOBANG,
                "item_gomoku_guard");
        putPlay(Game.TURTLE_SOUP,
                "item_turtle_probe");
        putPlay(Game.DOG_BATTLE,
                "item_battle_echo",
                "item_battle_pebble",
                "item_battle_airbag");

        putCommonPlay(Game.MINESWEEPER,
                "item_mine_mark",
                "item_mine_area",
                "item_mine_scout",
                "item_mine_shield");
        putCommonPlay(Game.DRAW_GUESS,
                "item_hint",
                "item_peek",
                "item_time",
                "item_inspiration");
        putCommonPlay(Game.QUICK_QUIZ,
                "item_quiz_score_pad");
        putCommonPlay(Game.DOG_BATTLE,
                "item_battle_echo");

        putInteraction(Game.TACIT_QUIZ,
                "item_sync_prophecy",
                "item_sync_perspective",
                "item_sync_secret_question");
        putInteraction(Game.QUICK_QUIZ,
                "item_quiz_duel",
                "item_prophecy");
        putInteraction(Game.GOBANG,
                "item_gomoku_prediction",
                "item_gomoku_review",
                "item_prophecy");
        putInteraction(Game.DOG_BATTLE,
                "item_battle_direct_hit",
                "item_prophecy");
    }

    private PetGameItemRules() {
    }

    public static GamePlayerPetItemsDTO normalize(Game game, GamePlayerPetItemsDTO petItems) {
        return normalize(game, null, petItems, itemId -> true);
    }

    public static GamePlayerPetItemsDTO normalize(Game game, String gameMode, GamePlayerPetItemsDTO petItems) {
        return normalize(game, gameMode, petItems, itemId -> true);
    }

    public static GamePlayerPetItemsDTO normalize(Game game, GamePlayerPetItemsDTO petItems,
                                                  Predicate<String> hasItem) {
        return normalize(game, null, petItems, hasItem);
    }

    public static GamePlayerPetItemsDTO normalize(Game game, String gameMode, GamePlayerPetItemsDTO petItems,
                                                  Predicate<String> hasItem) {
        if (petItems == null) {
            return new GamePlayerPetItemsDTO();
        }
        Predicate<String> ownership = hasItem == null ? itemId -> true : hasItem;
        String playItemId = isFormalMode(gameMode)
                ? null
                : normalizeSlot(PLAY_ITEMS, game, petItems.getPetPlayItemId(), ownership);
        String interactionItemId = normalizeSlot(INTERACTION_ITEMS, game, petItems.getPetInteractionItemId(), ownership);
        return new GamePlayerPetItemsDTO(playItemId, interactionItemId);
    }

    public static boolean isPlayItem(Game game, String itemId) {
        return contains(PLAY_ITEMS, game, itemId);
    }

    public static boolean isInteractionItem(Game game, String itemId) {
        return contains(INTERACTION_ITEMS, game, itemId);
    }

    public static boolean isWildCommonItem(String itemId) {
        return ITEM_WILD_COMMON.equals(trimToNull(itemId));
    }

    public static boolean isPartyEqualizerItem(String itemId) {
        return ITEM_PARTY_EQUALIZER.equals(trimToNull(itemId));
    }

    private static String normalizeSlot(Map<Game, Set<String>> itemsByGame, Game game, String itemId,
                                        Predicate<String> hasItem) {
        String normalizedItemId = trimToNull(itemId);
        if (itemsByGame == PLAY_ITEMS && isWildCommonItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (itemsByGame == PLAY_ITEMS && isPartyEqualizerItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (normalizedItemId == null || !contains(itemsByGame, game, normalizedItemId) || !hasItem.test(normalizedItemId)) {
            return null;
        }
        return normalizedItemId;
    }

    private static String normalizeCommonPlaySource(Game game, String sourceItemId, Predicate<String> hasItem) {
        if (game == null || sourceItemId == null || !hasItem.test(sourceItemId)) {
            return null;
        }
        Set<String> items = COMMON_PLAY_ITEMS.get(game);
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.iterator().next();
    }

    private static boolean contains(Map<Game, Set<String>> itemsByGame, Game game, String itemId) {
        String normalizedItemId = trimToNull(itemId);
        if (game == null || normalizedItemId == null) {
            return false;
        }
        Set<String> items = itemsByGame.get(game);
        return items != null && items.contains(normalizedItemId);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isFormalMode(String gameMode) {
        String normalized = trimToNull(gameMode);
        if (normalized == null) {
            return false;
        }
        return normalized.contains("正式")
                || normalized.contains("竞技")
                || normalized.contains("排位")
                || normalized.contains("排行")
                || normalized.toLowerCase().contains("rank");
    }

    private static void putPlay(Game game, String... itemIds) {
        PLAY_ITEMS.put(game, new HashSet<>(Arrays.asList(itemIds)));
    }

    private static void putCommonPlay(Game game, String... itemIds) {
        COMMON_PLAY_ITEMS.put(game, new LinkedHashSet<>(Arrays.asList(itemIds)));
    }

    private static void putInteraction(Game game, String... itemIds) {
        INTERACTION_ITEMS.put(game, new HashSet<>(Arrays.asList(itemIds)));
    }
}
