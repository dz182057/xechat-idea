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
        String playItemId = normalizeCarryItem(game, petItems.getPetPlayItemId(), ownership);
        String interactionItemId = normalizeCarryItem(game, petItems.getPetInteractionItemId(), ownership);
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

    public static boolean isCarryItem(Game game, String itemId) {
        return PetItemDefinitions.isCarryItem(game, itemId);
    }

    public static boolean isWildCommonItem(String itemId) {
        return PetItemDefinitions.isWildCommonItem(itemId);
    }

    public static boolean isPartyEqualizerItem(String itemId) {
        return PetItemDefinitions.isPartyEqualizerItem(itemId);
    }

    private static String normalizeCarryItem(Game game, String itemId, Predicate<String> hasItem) {
        String normalizedItemId = trimToNull(itemId);
        if (isWildCommonItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (isPartyEqualizerItem(normalizedItemId)) {
            return normalizeCommonPlaySource(game, normalizedItemId, hasItem);
        }
        if (normalizedItemId == null || !isCarryItem(game, normalizedItemId) || !hasItem.test(normalizedItemId)) {
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
