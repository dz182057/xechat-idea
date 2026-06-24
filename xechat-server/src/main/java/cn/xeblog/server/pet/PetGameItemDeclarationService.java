package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class PetGameItemDeclarationService {

    private static final int ITEM_DEFINITION_VERSION = 5;
    private static final int MAX_ITEM_COUNT = 9;
    private static final String SLOT_GAMEPLAY = "gameplay";
    private static final String SLOT_INTERACTION = "interaction";
    private static final String STATUS_RESERVED = "reserved";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_CONSUMED = "consumed";
    private static final String STATUS_REFUNDED = "refunded";
    private static final String ITEM_LEDGER_GAIN = "gain";
    private static final String ITEM_LEDGER_SPEND = "spend";
    private static final String ITEM_LEDGER_SOURCE_GAME_ITEM_RESERVE = "game_item_reserve";
    private static final String ITEM_LEDGER_SOURCE_GAME_ITEM_REFUND = "game_item_refund";
    private static final String ITEM_LEDGER_SOURCE_GAME_ITEM_INTERACTION_RETURN = "game_item_interaction_return";
    private static OwnershipChecker ownershipChecker = PetProfileService::hasPositiveItem;

    private PetGameItemDeclarationService() {
    }

    public static GamePlayerPetItemsDTO normalizeForUser(User user, Game game, GamePlayerPetItemsDTO petItems) {
        long accountId = user == null ? 0L : user.getAccountId();
        int carrySlotLimit = PetProfileService.itemCarrySlotLimit(accountId);
        return PetGameItemRules.normalize(game, null, petItems, itemId ->
                accountId > 0L && ownershipChecker.hasPositiveItem(accountId, itemId), carrySlotLimit);
    }

    public static GamePlayerPetItemsDTO applyDeclarationForUser(User user, GameRoom room,
                                                                GamePlayerPetItemsDTO petItems) {
        if (user == null || room == null || !room.isPlayerConnection(user)) {
            return new GamePlayerPetItemsDTO();
        }
        GameRoom.Player player = room.getUsers().get(user.getIdentityKey());
        if (player == null || player.getAccountId() <= 0L) {
            return new GamePlayerPetItemsDTO();
        }

        boolean partyEqualizerRequested = petItems != null
                && PetGameItemRules.isPartyEqualizerItem(petItems.getPetPlayItemId());
        int carrySlotLimit = PetProfileService.itemCarrySlotLimit(player.getAccountId());
        GamePlayerPetItemsDTO normalized = PetGameItemRules.normalize(room.getGame(), room.getGameMode(), petItems,
                itemId -> PetProfileService.hasPositiveItem(player.getAccountId(), itemId), carrySlotLimit);
        synchronized (player) {
            String playItemId = updateReservedSlot(room, player, player.getPetPlayItemId(),
                    normalized.getPetPlayItemId(),
                    reservationSourceItemId(petItems == null ? null : petItems.getPetPlayItemId(),
                            normalized.getPetPlayItemId()),
                    SLOT_GAMEPLAY);
            String interactionItemId = updateReservedSlot(room, player, player.getPetInteractionItemId(),
                    normalized.getPetInteractionItemId(),
                    normalized.getPetInteractionItemId(),
                    SLOT_INTERACTION);
            GamePlayerPetItemsDTO applied = new GamePlayerPetItemsDTO(playItemId, interactionItemId);
            room.applyPetItems(user, applied);
            if (partyEqualizerRequested && playItemId != null) {
                applyTemporaryPlayItemForOtherPlayers(room, user.getIdentityKey(), playItemId);
            }
            return applied;
        }
    }

    public static void releaseReservedForPlayer(GameRoom room, User user) {
        if (room == null || user == null || !room.isPlayerConnection(user)) {
            return;
        }
        GameRoom.Player player = room.getUsers().get(user.getIdentityKey());
        if (player == null) {
            return;
        }
        releaseReservedForPlayer(room, player);
        room.applyPetItems(user, new GamePlayerPetItemsDTO());
    }

    public static void releaseReservedForRoom(GameRoom room) {
        if (room == null) {
            return;
        }
        for (GameRoom.Player player : room.getUsers().values()) {
            releaseReservedForPlayer(room, player);
            player.setPetPlayItemId(null);
            player.setPetInteractionItemId(null);
        }
    }

    public static void settleSucceeded(GameRoom room, String playerKey, String itemId, String slot, int rewardBones) {
        settleReservedItem(room, playerKey, itemId, slot, STATUS_SUCCEEDED, rewardBones, true);
    }

    public static int settleSucceededWithInteractionReward(GameRoom room, String playerKey, String itemId,
                                                            String slot, int requestedRewardBones) {
        return settleReservedItemWithInteractionReward(room, playerKey, itemId, slot, requestedRewardBones);
    }

    public static void settleFailed(GameRoom room, String playerKey, String itemId, String slot) {
        settleReservedItem(room, playerKey, itemId, slot, STATUS_FAILED, 0, false);
    }

    public static void settleConsumed(GameRoom room, String playerKey, String itemId, String slot) {
        settleReservedItem(room, playerKey, itemId, slot, STATUS_CONSUMED, 0, false);
    }

    public static void settleRefunded(GameRoom room, String playerKey, String itemId, String slot) {
        settleReservedItem(room, playerKey, itemId, slot, STATUS_REFUNDED, 0, true);
    }

    private static void releaseReservedForPlayer(GameRoom room, GameRoom.Player player) {
        if (player == null || player.getAccountId() <= 0L) {
            return;
        }
        synchronized (player) {
            refundReservedItem(room, player, player.getPetPlayItemId(), SLOT_GAMEPLAY);
            refundReservedItem(room, player, player.getPetInteractionItemId(), SLOT_INTERACTION);
        }
    }

    private static String updateReservedSlot(GameRoom room, GameRoom.Player player, String currentItemId,
                                             String requestedItemId, String sourceItemId, String slot) {
        if (sameItem(currentItemId, requestedItemId)) {
            return currentItemId;
        }
        refundReservedItem(room, player, currentItemId, slot);
        if (requestedItemId == null) {
            return null;
        }
        return reserveItem(room, player, sourceItemId, requestedItemId, slot) ? requestedItemId : null;
    }

    private static boolean reserveItem(GameRoom room, GameRoom.Player player, String sourceItemId,
                                       String itemId, String slot) {
        if (sourceItemId == null || itemId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (accountLock(player.getAccountId())) {
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
                if (itemMapper.decrementItemIfEnough(player.getAccountId(), sourceItemId, 1, now) <= 0) {
                    session.rollback();
                    return false;
                }
                PetGameItemUseRecord record = new PetGameItemUseRecord();
                record.setId(UUID.randomUUID().toString());
                record.setGameId(room.getId());
                record.setAccountId(player.getAccountId());
                record.setItemId(itemId);
                record.setSlot(slot);
                record.setDefinitionVersion(ITEM_DEFINITION_VERSION);
                record.setStatus(STATUS_RESERVED);
                record.setRewardBones(0);
                record.setCreatedAt(now);
                session.getMapper(PetGameItemUseMapper.class).insert(record);
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), player.getAccountId(),
                        sourceItemId, 1, ITEM_LEDGER_SPEND,
                        ITEM_LEDGER_SOURCE_GAME_ITEM_RESERVE, record.getId(), now);
                session.commit();
                return true;
            }
        }
    }

    private static String reservationSourceItemId(String requestedItemId, String effectiveItemId) {
        if (effectiveItemId == null) {
            return null;
        }
        String normalizedRequest = trimToNull(requestedItemId);
        return PetGameItemRules.isWildCommonItem(normalizedRequest) || PetGameItemRules.isPartyEqualizerItem(normalizedRequest)
                ? normalizedRequest
                : effectiveItemId;
    }

    private static void applyTemporaryPlayItemForOtherPlayers(GameRoom room, String sourcePlayerKey, String itemId) {
        if (room == null || itemId == null) {
            return;
        }
        for (Map.Entry<String, GameRoom.Player> entry : room.getUsers().entrySet()) {
            GameRoom.Player player = entry.getValue();
            if (sourcePlayerKey != null && sourcePlayerKey.equals(entry.getKey())) {
                continue;
            }
            if (player == null || player.getAccountId() <= 0L || player.getPetPlayItemId() != null) {
                continue;
            }
            player.setPetPlayItemId(itemId);
        }
    }

    private static void refundReservedItem(GameRoom room, GameRoom.Player player, String itemId, String slot) {
        if (itemId == null || player.getAccountId() <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (accountLock(player.getAccountId())) {
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetGameItemUseMapper useMapper = session.getMapper(PetGameItemUseMapper.class);
                PetGameItemUseRecord record = useMapper.findLatestReserved(
                        room.getId(), player.getAccountId(), itemId, slot);
                if (record == null) {
                    session.rollback();
                    return;
                }
                if (session.getMapper(PetItemMapper.class)
                        .addItemIfUnderLimit(player.getAccountId(), itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
                    session.rollback();
                    return;
                }
                if (useMapper.markSettled(record.getId(), STATUS_REFUNDED, 0, now) <= 0) {
                    session.rollback();
                    return;
                }
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), player.getAccountId(),
                        itemId, 1, ITEM_LEDGER_GAIN,
                        ITEM_LEDGER_SOURCE_GAME_ITEM_REFUND, record.getId(), now);
                session.commit();
            }
        }
    }

    private static void settleReservedItem(GameRoom room, String playerKey, String itemId, String slot,
                                           String status, int rewardBones, boolean refundItem) {
        if (room == null || playerKey == null || itemId == null || slot == null) {
            return;
        }
        GameRoom.Player player = room.getUsers().get(playerKey);
        if (player == null || player.getAccountId() <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (accountLock(player.getAccountId())) {
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetGameItemUseMapper useMapper = session.getMapper(PetGameItemUseMapper.class);
                PetGameItemUseRecord record = useMapper.findLatestReserved(
                        room.getId(), player.getAccountId(), itemId, slot);
                if (record == null) {
                    session.rollback();
                    return;
                }
                if (refundItem && session.getMapper(PetItemMapper.class)
                        .addItemIfUnderLimit(player.getAccountId(), itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
                    session.rollback();
                    return;
                }
                if (useMapper.markSettled(record.getId(), status, rewardBones, now) <= 0) {
                    session.rollback();
                    return;
                }
                if (refundItem) {
                    recordItemLedger(session.getMapper(PetItemLedgerMapper.class), player.getAccountId(),
                            itemId, 1, ITEM_LEDGER_GAIN,
                            ITEM_LEDGER_SOURCE_GAME_ITEM_REFUND, record.getId(), now);
                }
                session.commit();
            }
        }
    }

    private static int settleReservedItemWithInteractionReward(GameRoom room, String playerKey, String itemId,
                                                                String slot, int requestedRewardBones) {
        if (room == null || playerKey == null || itemId == null || slot == null) {
            return 0;
        }
        GameRoom.Player player = room.getUsers().get(playerKey);
        if (player == null || player.getAccountId() <= 0L) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (accountLock(player.getAccountId())) {
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetGameItemUseMapper useMapper = session.getMapper(PetGameItemUseMapper.class);
                PetGameItemUseRecord record = useMapper.findLatestReserved(
                        room.getId(), player.getAccountId(), itemId, slot);
                if (record == null) {
                    session.rollback();
                    return 0;
                }
                if (session.getMapper(PetItemMapper.class)
                        .addItemIfUnderLimit(player.getAccountId(), itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
                    session.rollback();
                    return 0;
                }
                int acceptedReward = PetProfileService.applyInteractionItemRewardInSession(
                        session,
                        player.getAccountId(),
                        itemId,
                        requestedRewardBones,
                        now,
                        LocalDate.now().toString());
                if (useMapper.markSettled(record.getId(), STATUS_SUCCEEDED, acceptedReward, now) <= 0) {
                    session.rollback();
                    return 0;
                }
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), player.getAccountId(),
                        itemId, 1, ITEM_LEDGER_GAIN,
                        ITEM_LEDGER_SOURCE_GAME_ITEM_INTERACTION_RETURN, record.getId(), now);
                session.commit();
                return acceptedReward;
            }
        }
    }

    private static boolean sameItem(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void recordItemLedger(PetItemLedgerMapper mapper, long accountId, String itemId, int quantity,
                                         String direction, String source, String sourceRef, long now) {
        if (accountId <= 0 || itemId == null || itemId.isBlank() || quantity <= 0) {
            return;
        }
        PetItemLedgerRecord record = new PetItemLedgerRecord();
        record.setId(UUID.randomUUID().toString());
        record.setAccountId(accountId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setDirection(direction);
        record.setSource(source);
        record.setSourceRef(sourceRef);
        record.setMetadataJson(null);
        record.setCreatedAt(now);
        mapper.insert(record);
    }

    private static Object accountLock(long accountId) {
        return ("pet-game-item:" + accountId).intern();
    }

    static void setOwnershipCheckerForTest(OwnershipChecker checker) {
        ownershipChecker = checker == null ? PetProfileService::hasPositiveItem : checker;
    }

    static void resetOwnershipCheckerForTest() {
        ownershipChecker = PetProfileService::hasPositiveItem;
    }

    interface OwnershipChecker {
        boolean hasPositiveItem(long accountId, String itemId);
    }
}
