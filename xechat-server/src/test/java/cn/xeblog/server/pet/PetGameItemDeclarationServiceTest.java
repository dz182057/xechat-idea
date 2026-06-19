package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.game.dogbattle.DogBattleService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PetGameItemDeclarationServiceTest {

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-pet-item-declaration-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();
    }

    @After
    public void tearDown() throws Exception {
        PetGameItemDeclarationService.resetOwnershipCheckerForTest();
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void normalizeForUserKeepsOnlyLegalOwnedItems() {
        User user = new User();
        user.setAccountId(1001L);
        Set<String> ownedItems = new HashSet<>();
        ownedItems.add("item_battle_echo");
        PetGameItemDeclarationService.setOwnershipCheckerForTest(
                (accountId, itemId) -> accountId == 1001L && ownedItems.contains(itemId));

        GamePlayerPetItemsDTO normalized = PetGameItemDeclarationService.normalizeForUser(
                user,
                Game.DOG_BATTLE,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit"));

        Assert.assertEquals("item_battle_echo", normalized.getPetPlayItemId());
        Assert.assertNull(normalized.getPetInteractionItemId());
    }

    @Test
    public void applyDeclarationForUserReservesOwnedItems() {
        User user = user(1002L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_echo", 1);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);

        GamePlayerPetItemsDTO normalized = PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit"));

        Assert.assertEquals("item_battle_echo", normalized.getPetPlayItemId());
        Assert.assertEquals("item_battle_direct_hit", normalized.getPetInteractionItemId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(), "item_battle_echo", "gameplay", "reserved"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(), "item_battle_direct_hit", "interaction", "reserved"));
    }

    @Test
    public void releaseReservedForPlayerRefundsDeclaredItems() {
        User user = user(1003L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_echo", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_battle_echo", null));

        PetGameItemDeclarationService.releaseReservedForPlayer(room, user);

        Assert.assertNull(room.getUsers().get(user.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(), "item_battle_echo", "gameplay", "refunded"));
    }

    @Test
    public void settleSucceededRefundsItemAndMarksUsageSucceeded() {
        User user = user(1004L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        PetGameItemDeclarationService.settleSucceeded(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction",
                40);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
    }

    @Test
    public void settleSucceededWithInteractionRewardCommitsItemAndRewardTogether() {
        User user = user(1008L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction",
                40);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(340, findBones(user.getAccountId()));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
        Assert.assertEquals(40, findUsageRewardBones(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
    }

    @Test
    public void dogBattleDefaultSucceededSettlementAppliesInteractionReward() throws Exception {
        User user = user(1010L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        Method settleGameItem = DogBattleService.class.getDeclaredMethod(
                "settleGameItem",
                GameRoom.class,
                String.class,
                String.class,
                String.class,
                String.class,
                int.class);
        settleGameItem.setAccessible(true);
        settleGameItem.invoke(null, room, user.getIdentityKey(),
                "item_battle_direct_hit", "interaction", "succeeded", 40);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(340, findBones(user.getAccountId()));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
        Assert.assertEquals(40, findUsageRewardBones(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
    }

    @Test
    public void settleSucceededWithInteractionRewardRollsBackWhenRewardFails() {
        User user = user(1009L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        try {
            PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                    room,
                    user.getIdentityKey(),
                    "item_battle_direct_hit",
                    "interaction",
                    -1);
            Assert.fail("负数互动奖励应回滚整笔结算");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("互动奖励必须为正数", expected.getMessage());
        }

        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(0, findBones(user.getAccountId()));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "reserved"));
        Assert.assertEquals(0, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
    }

    @Test
    public void settleSucceededWithInteractionRewardIsIdempotent() {
        User user = user(1011L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction",
                40);
        PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction",
                40);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(340, findBones(user.getAccountId()));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
        Assert.assertEquals(0, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "reserved"));
    }

    @Test
    public void settleFailedConsumesItemAndMarksUsageFailed() {
        User user = user(1005L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        PetGameItemDeclarationService.settleFailed(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction");

        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "failed"));
    }

    @Test
    public void settleConsumedKeepsItemSpentAndMarksUsageConsumed() {
        User user = user(1006L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_pebble", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_battle_pebble", null));

        PetGameItemDeclarationService.settleConsumed(
                room,
                user.getIdentityKey(),
                "item_battle_pebble",
                "gameplay");

        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_pebble"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_pebble", "gameplay", "consumed"));
    }

    @Test
    public void settleConsumedIsIdempotentAfterFirstSettlement() {
        User user = user(1012L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_pebble", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_battle_pebble", null));

        PetGameItemDeclarationService.settleConsumed(
                room,
                user.getIdentityKey(),
                "item_battle_pebble",
                "gameplay");
        PetGameItemDeclarationService.settleConsumed(
                room,
                user.getIdentityKey(),
                "item_battle_pebble",
                "gameplay");

        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_pebble"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_pebble", "gameplay", "consumed"));
        Assert.assertEquals(0, countUsages(room.getId(), user.getAccountId(),
                "item_battle_pebble", "gameplay", "reserved"));
    }

    @Test
    public void refundedItemCannotBeSettledAgain() {
        User user = user(1013L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO(null, "item_battle_direct_hit"));

        PetGameItemDeclarationService.releaseReservedForPlayer(room, user);
        PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                room,
                user.getIdentityKey(),
                "item_battle_direct_hit",
                "interaction",
                40);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_direct_hit"));
        Assert.assertEquals(0, findBones(user.getAccountId()));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "refunded"));
        Assert.assertEquals(0, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "succeeded"));
    }

    @Test
    public void applyDeclarationDoesNotReservePlayItemInFormalMode() {
        User user = user(1007L);
        GameRoom room = room(user);
        room.setGameMode("正式模式");
        insertPetItem(user.getAccountId(), "item_battle_echo", 1);
        insertPetItem(user.getAccountId(), "item_battle_direct_hit", 1);

        GamePlayerPetItemsDTO normalized = PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_battle_echo", "item_battle_direct_hit"));

        Assert.assertNull(normalized.getPetPlayItemId());
        Assert.assertEquals("item_battle_direct_hit", normalized.getPetInteractionItemId());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(0, countUsages(room.getId(), user.getAccountId(), "item_battle_echo", "gameplay", "reserved"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_direct_hit", "interaction", "reserved"));
    }

    @Test
    public void applyDeclarationConvertsWildCommonAndReservesConvertedPlayItem() {
        User user = user(1014L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_wild_common", 1);

        GamePlayerPetItemsDTO normalized = PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_wild_common", null));

        Assert.assertEquals("item_battle_echo", normalized.getPetPlayItemId());
        Assert.assertNull(normalized.getPetInteractionItemId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_wild_common"));
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_echo", "gameplay", "reserved"));

        PetGameItemDeclarationService.releaseReservedForPlayer(room, user);

        Assert.assertNull(room.getUsers().get(user.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_wild_common"));
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_echo", "gameplay", "refunded"));
    }

    @Test
    public void applyDeclarationConvertsPartyEqualizerAndGivesTemporaryPlayItemToOtherPlayers() {
        User user = user(1015L);
        User other = user(2015L);
        GameRoom room = room(user);
        room.addUser(other);
        insertPetItem(user.getAccountId(), "item_party_equalizer", 1);

        GamePlayerPetItemsDTO normalized = PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_party_equalizer", null));

        Assert.assertEquals("item_battle_echo", normalized.getPetPlayItemId());
        Assert.assertEquals("item_battle_echo", room.getUsers().get(other.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_party_equalizer"));
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(0, countItem(other.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_battle_echo", "gameplay", "reserved"));

        PetGameItemDeclarationService.releaseReservedForRoom(room);

        Assert.assertNull(room.getUsers().get(user.getIdentityKey()).getPetPlayItemId());
        Assert.assertNull(room.getUsers().get(other.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_party_equalizer"));
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_battle_echo"));
        Assert.assertEquals(0, countItem(other.getAccountId(), "item_battle_echo"));
    }

    private static User user(long accountId) {
        User user = new User();
        user.setId("channel-" + accountId);
        user.setAccountId(accountId);
        user.setAccount("account-" + accountId);
        user.setNickname("玩家" + accountId);
        user.setUuid("uuid-" + accountId);
        return user;
    }

    private static GameRoom room(User user) {
        GameRoom room = new GameRoom();
        room.setId("declaration-room-" + user.getAccountId());
        room.setGame(Game.DOG_BATTLE);
        room.setNums(2);
        room.addUser(user);
        return room;
    }

    private static void insertPetItem(long accountId, String itemId, int count) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_items (account_id, item_id, count, updated_at) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countItem(long accountId, String itemId) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(count), 0) FROM pet_items WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countUsages(long roomId, long accountId, String itemId, String slot, String status) {
        return countUsages(String.valueOf(roomId), accountId, itemId, slot, status);
    }

    private static int countUsages(String roomId, long accountId, String itemId, String slot, String status) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM game_item_uses " +
                             "WHERE game_id = ? AND account_id = ? AND item_id = ? AND slot = ? AND status = ?")) {
            statement.setString(1, roomId);
            statement.setLong(2, accountId);
            statement.setString(3, itemId);
            statement.setString(4, slot);
            statement.setString(5, status);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int findBones(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(bones), 0) FROM pet_assets WHERE account_id = ?")) {
            statement.setLong(1, accountId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int findUsageRewardBones(String roomId, long accountId, String itemId, String slot, String status) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(reward_bones), 0) FROM game_item_uses " +
                             "WHERE game_id = ? AND account_id = ? AND item_id = ? AND slot = ? AND status = ?")) {
            statement.setString(1, roomId);
            statement.setLong(2, accountId);
            statement.setString(3, itemId);
            statement.setString(4, slot);
            statement.setString(5, status);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void resetFactory() throws Exception {
        Field field = DbInitializer.class.getDeclaredField("FACTORY");
        field.setAccessible(true);
        SqlSessionFactory factory = (SqlSessionFactory) field.get(null);
        if (factory != null) {
            factory.getConfiguration().getEnvironment().getDataSource();
        }
        field.set(null, null);
    }
}
