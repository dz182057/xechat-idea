package cn.xeblog.server.pet;

import cn.xeblog.commons.enums.Game;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class PetItemDefinition {

    public enum Rarity {
        COMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    public enum Slot {
        PLAY,
        INTERACTION,
        UTILITY
    }

    public enum ReleaseStage {
        CORE,
        EXPANSION
    }

    private final String itemId;
    private final Rarity rarity;
    private final ReleaseStage releaseStage;
    private final Slot slot;
    private final int sellPrice;
    private final Integer interactionRewardBones;
    private final boolean formalModeAllowed;
    private final boolean commonPlayTarget;
    private final Set<Game> relatedGames;

    PetItemDefinition(String itemId, Rarity rarity, ReleaseStage releaseStage, Slot slot, int sellPrice,
                      Integer interactionRewardBones, boolean formalModeAllowed, boolean commonPlayTarget,
                      Game... relatedGames) {
        this.itemId = itemId;
        this.rarity = rarity;
        this.releaseStage = releaseStage;
        this.slot = slot;
        this.sellPrice = sellPrice;
        this.interactionRewardBones = interactionRewardBones;
        this.formalModeAllowed = formalModeAllowed;
        this.commonPlayTarget = commonPlayTarget;
        if (relatedGames == null || relatedGames.length == 0) {
            this.relatedGames = Collections.emptySet();
        } else {
            EnumSet<Game> games = EnumSet.noneOf(Game.class);
            Collections.addAll(games, relatedGames);
            this.relatedGames = Collections.unmodifiableSet(games);
        }
    }

    public String getItemId() {
        return itemId;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public ReleaseStage getReleaseStage() {
        return releaseStage;
    }

    public Slot getSlot() {
        return slot;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public Integer getInteractionRewardBones() {
        return interactionRewardBones;
    }

    public boolean isFormalModeAllowed() {
        return formalModeAllowed;
    }

    public boolean isCommonPlayTarget() {
        return commonPlayTarget;
    }

    public Set<Game> getRelatedGames() {
        return relatedGames;
    }
}
