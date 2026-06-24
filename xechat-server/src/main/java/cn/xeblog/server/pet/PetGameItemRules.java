package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.enums.Game;

import java.util.function.Predicate;

public final class PetGameItemRules {

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
        return normalize(game, gameMode, petItems, hasItem, 2);
    }

    public static GamePlayerPetItemsDTO normalize(Game game, String gameMode, GamePlayerPetItemsDTO petItems,
                                                  Predicate<String> hasItem, int carrySlotLimit) {
        if (petItems == null) {
            return new GamePlayerPetItemsDTO();
        }
        Predicate<String> ownership = hasItem == null ? itemId -> true : hasItem;
        String playItemId = isFormalMode(gameMode)
                ? null
                : normalizeSlot(true, game, petItems.getPetPlayItemId(), ownership);
        String interactionItemId = normalizeSlot(false, game, petItems.getPetInteractionItemId(), ownership);
        int limit = Math.max(1, carrySlotLimit);
        if (limit <= 1 && playItemId != null && interactionItemId != null) {
            interactionItemId = null;
        }
        return new GamePlayerPetItemsDTO(playItemId, interactionItemId);
    }

    public static boolean isPlayItem(Game game, String itemId) {
        return PetItemDefinitions.isPlayItem(game, itemId);
    }

    public static boolean isInteractionItem(Game game, String itemId) {
        return PetItemDefinitions.isInteractionItem(game, itemId);
    }

    public static boolean isWildCommonItem(String itemId) {
        return PetItemDefinitions.isWildCommonItem(itemId);
    }

    public static boolean isPartyEqualizerItem(String itemId) {
        return PetItemDefinitions.isPartyEqualizerItem(itemId);
    }

    private static String normalizeSlot(boolean playSlot, Game game, String itemId, Predicate<String> hasItem) {
        String normalizedItemId = trimToNull(itemId);
        if (playSlot && isWildCommonItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (playSlot && isPartyEqualizerItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (normalizedItemId == null || !contains(playSlot, game, normalizedItemId) || !hasItem.test(normalizedItemId)) {
            return null;
        }
        return normalizedItemId;
    }

    private static String normalizeCommonPlaySource(Game game, String sourceItemId, Predicate<String> hasItem) {
        if (game == null || sourceItemId == null || !hasItem.test(sourceItemId)) {
            return null;
        }
        return PetItemDefinitions.firstCommonPlayItem(game);
    }

    private static boolean contains(boolean playSlot, Game game, String itemId) {
        String normalizedItemId = trimToNull(itemId);
        if (game == null || normalizedItemId == null) {
            return false;
        }
        return playSlot
                ? PetItemDefinitions.isPlayItem(game, normalizedItemId)
                : PetItemDefinitions.isInteractionItem(game, normalizedItemId);
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
}
