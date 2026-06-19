package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetCheckinMilestoneRewardDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDefinitionDTO;
import cn.xeblog.commons.entity.pet.PetWalkDogDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PetServiceTest {

    private static final Set<String> FIRST_LAUNCH_RARE_ITEM_IDS = new HashSet<>(Arrays.asList(
            "item_mine_guard",
            "item_metal_detector",
            "item_draw_inspiration",
            "item_draw_peek",
            "item_draw_time",
            "item_draw_replay",
            "item_sync_perspective",
            "item_sync_secret_question",
            "item_quiz_wrong_option",
            "item_gomoku_guard",
            "item_turtle_probe",
            "item_battle_pebble",
            "item_battle_airbag",
            "item_race_knee"
    ));

    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("xechat-pet-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
    }

    @After
    public void tearDown() throws Exception {
        resetDbFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void profileShouldCreateDefaultAssetsWithoutDogs() {
        PetProfileDTO profile = PetService.profile(accountUser(990001L));

        Assert.assertEquals(990001L, profile.getAccountId());
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertNotNull(profile.getCheckinStatus().getServerDate());
    }

    @Test
    public void profileShouldExposeV5TrainerManualSkillPool() {
        PetProfileDTO profile = PetService.profile(accountUser(990015L));

        Assert.assertNotNull(profile.getTrainingStatus());
        Assert.assertEquals("v5-explore-training", profile.getTrainingStatus().getDefinitionVersion());
        Assert.assertEquals(Arrays.asList(100, 150, 300, 500, 800),
                profile.getTrainingStatus().getUpgradeCosts());
        Assert.assertFalse(profile.getTrainingStatus().isFreeLearnAvailable());
        Assert.assertEquals(5, profile.getTrainingStatus().getDefinitions().size());
        PetTrainingSkillDefinitionDTO route = profile.getTrainingStatus().getDefinitions().get(0);
        Assert.assertEquals("explore_route", route.getSkillId());
        Assert.assertEquals("熟路口令", route.getName());
        Assert.assertEquals("耗时 -4%", route.getLevelEffects().get(0));
        Assert.assertTrue(profile.getTrainingStatus().getSkills().isEmpty());
    }

    @Test
    public void adoptShouldCreateFirstDogAndAllowKennelGrowthPastActivitySlots() {
        User user = accountUser(990002L);

        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "小白"));

        Assert.assertEquals(1, profile.getDogs().size());
        PetDogDTO dog = profile.getDogs().get(0);
        Assert.assertEquals("小白", dog.getName());
        Assert.assertEquals("corgi", dog.getBreed());
        Assert.assertEquals("puppy", dog.getStage());
        Assert.assertEquals(10, dog.getEnergy());
        Assert.assertEquals(0, dog.getWeeklyPoints());

        PetProfileDTO afterSecond = PetService.adopt(user, adopt("golden", "小黄"));

        Assert.assertEquals(1, afterSecond.getAssets().getDogSlots());
        Assert.assertEquals(2, afterSecond.getDogs().size());
        Assert.assertEquals("小黄", afterSecond.getDogs().get(1).getName());
        Assert.assertEquals("golden", afterSecond.getDogs().get(1).getBreed());
    }

    @Test
    public void adoptHiddenBreedRequiresUnlock() {
        User user = accountUser(990011L);

        try {
            PetService.adopt(user, adopt("husky", "小哈"));
            Assert.fail("哈士奇未解锁时不应允许领养");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("该隐藏品种尚未解锁", e.getMessage());
        }

        try {
            PetService.adopt(user, adopt("shiba", "小柴"));
            Assert.fail("柴犬未解锁时不应允许领养");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("该隐藏品种尚未解锁", e.getMessage());
        }
    }

    @Test
    public void adoptHuskyRequiresMysteryCaveCompletionAfterTreasureMapUnlock() throws Exception {
        User user = accountUser(990012L);
        PetService.adopt(user, adopt("corgi", "小白"));
        addTreasureMapFragments(user.getAccountId(), 3);

        try {
            PetService.adopt(user, adopt("husky", "小哈"));
            Assert.fail("只集齐藏宝图碎片时不应允许领养哈士奇");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("该隐藏品种尚未解锁", e.getMessage());
        }

        markMysteryCaveCompleted(user.getAccountId());

        PetProfileDTO profile = PetService.adopt(user, adopt("husky", "小哈"));

        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertEquals(2, profile.getDogs().size());
        PetDogDTO husky = profile.getDogs().get(1);
        Assert.assertEquals("小哈", husky.getName());
        Assert.assertEquals("husky", husky.getBreed());
        Assert.assertTrue(profile.getExploreStatus().isMysteryCaveCompleted());
        Assert.assertTrue(profile.getExploreStatus().isHuskyUnlocked());
    }

    @Test
    public void adoptShibaAfterThreeFirstPlaceResults() {
        User user = accountUser(990013L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "小白"));
        String dogId = profile.getDogs().get(0).getId();
        PetService.applyRaceResult(user, raceResult(dogId, 1));
        PetService.applyRaceResult(user, raceResult(dogId, 1));
        PetService.applyRaceResult(user, raceResult(dogId, 1));

        PetProfileDTO afterAdopt = PetService.adopt(user, adopt("shiba", "小柴"));

        Assert.assertEquals(2, afterAdopt.getDogs().size());
        PetDogDTO shiba = afterAdopt.getDogs().get(1);
        Assert.assertEquals("小柴", shiba.getName());
        Assert.assertEquals("shiba", shiba.getBreed());
    }

    @Test
    public void raceResultShouldAdvanceRaceCountersAndAdultStage() {
        User user = accountUser(990003L);
        PetProfileDTO profile = PetService.adopt(user, adopt("native", "赛跑狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetService.applyRaceResult(user, raceResult(dogId, 2));
        PetService.applyRaceResult(user, raceResult(dogId, 2));
        PetProfileDTO afterThirdRace = PetService.applyRaceResult(user, raceResult(dogId, 1));

        PetDogDTO dog = afterThirdRace.getDogs().get(0);
        Assert.assertEquals(3, dog.getRaceCount());
        Assert.assertEquals(1, dog.getRaceFirstCount());
    }

    @Test
    public void raceSignupShouldSpendBonesAndDogEnergyAtomically() {
        User user = accountUser(990004L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "报名狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetProfileDTO afterSignup = PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);

        Assert.assertEquals(280, afterSignup.getAssets().getBones());
        Assert.assertEquals(7, afterSignup.getDogs().get(0).getEnergy());
    }

    @Test
    public void raceSignupShouldNotSpendBonesWhenEnergyIsNotEnough() {
        User user = accountUser(990005L);
        PetProfileDTO profile = PetService.adopt(user, adopt("golden", "低活力狗"));
        String dogId = profile.getDogs().get(0).getId();
        PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);
        PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);
        PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);

        try {
            PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);
            Assert.fail("活力不足时不应继续报名扣费");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("狗狗活力不足", e.getMessage());
        }

        PetProfileDTO afterFailure = PetService.profile(user);
        Assert.assertEquals(240, afterFailure.getAssets().getBones());
        Assert.assertEquals(1, afterFailure.getDogs().get(0).getEnergy());
    }

    @Test
    public void raceWeeklyPointsShouldAccumulateOnDog() {
        User user = accountUser(990006L);
        PetProfileDTO profile = PetService.adopt(user, adopt("native", "周榜狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetService.applyRaceResult(user, raceResult(dogId, 2, 6));
        PetProfileDTO afterSecond = PetService.applyRaceResult(user, raceResult(dogId, 1, 10));

        Assert.assertEquals(16, afterSecond.getDogs().get(0).getWeeklyPoints());
    }

    @Test
    public void profileShouldClampDogStatsToDesignRange() throws Exception {
        User user = accountUser(990007L);
        PetProfileDTO profile = PetService.adopt(user, adopt("native", "上限狗"));
        String dogId = profile.getDogs().get(0).getId();
        updateDogStats(user.getAccountId(), dogId, 120, 101, 100, -5, 130);

        PetDogDTO dog = PetService.profile(user).getDogs().get(0);

        Assert.assertEquals(100, dog.getSpeed());
        Assert.assertEquals(100, dog.getStamina());
        Assert.assertEquals(100, dog.getBurst());
        Assert.assertEquals(0, dog.getWisdom());
        Assert.assertEquals(100, dog.getBond());
    }

    @Test
    public void checkinShouldKeepDogBondUnchanged() {
        User user = accountUser(990008L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "签到狗"));
        int beforeBond = profile.getDogs().get(0).getBond();

        PetDogDTO dog = PetProfileService.checkin(user.getAccountId()).getDogs().get(0);

        Assert.assertEquals(beforeBond, dog.getBond());
    }

    @Test
    public void checkinShouldAddBackHillCollectionBonusWhenSetCompleted() throws Exception {
        User user = accountUser(990014L);
        PetProfileDTO beforeProfile = PetService.profile(user);
        insertBackHillCollections(user.getAccountId());

        PetProfileDTO afterProfile = PetProfileService.checkin(user.getAccountId());

        Assert.assertEquals(beforeProfile.getAssets().getBones() + 25, afterProfile.getAssets().getBones());
    }

    @Test
    public void checkinShouldGrantMilestoneDecorationAndRareItemEvery28Checkins() throws Exception {
        User user = accountUser(990017L);
        PetService.profile(user);
        insertCheckins(user.getAccountId(), 27);

        PetProfileDTO profile = PetProfileService.checkin(user.getAccountId());

        PetCheckinMilestoneRewardDTO reward = profile.getCheckinStatus().getLastMilestoneReward();
        Assert.assertNotNull(reward);
        Assert.assertEquals(1, reward.getMilestoneIndex());
        Assert.assertEquals("checkin_decoration_hat", reward.getDecorationId());
        Assert.assertTrue(FIRST_LAUNCH_RARE_ITEM_IDS.contains(reward.getItemId()));
        Assert.assertEquals(0, reward.getOverflowBones());
        Assert.assertEquals(28, profile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(28, profile.getCheckinStatus().getMilestoneRemaining());
        Assert.assertEquals(1, findCollectionCount(user.getAccountId(), reward.getDecorationId()));
        Assert.assertEquals(1, findItemCount(user.getAccountId(), reward.getItemId()));
    }

    @Test
    public void buyFoodShouldUseCreekCollectionDiscountWhenSetCompleted() throws Exception {
        User user = accountUser(990018L);
        insertCreekCollections(user.getAccountId());

        PetProfileDTO profile = PetProfileService.shopBuy(user.getAccountId(), shopBuy("food", 2));

        Assert.assertEquals(250, profile.getAssets().getBones());
        Assert.assertEquals(8, profile.getAssets().getFood());
    }

    @Test
    public void profileShouldApplySnowMountainCollectionEnergyLimitBonus() throws Exception {
        User user = accountUser(990019L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "雪山狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertSnowMountainCollections(user.getAccountId());
        setDogEnergy(user.getAccountId(), dogId, 1, "2000-01-01");

        PetProfileDTO profile = PetService.profile(user);

        Assert.assertEquals(20, profile.getAssets().getEnergyLimit());
        Assert.assertEquals(20, profile.getDogs().get(0).getEnergy());
    }

    @Test
    public void feedShouldRestoreEnergyUpToSnowMountainCollectionLimit() throws Exception {
        User user = accountUser(990020L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "雪山饭狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertSnowMountainCollections(user.getAccountId());
        setDogEnergy(user.getAccountId(), dogId, 10, LocalDate.now().toString());

        PetProfileDTO profile = PetProfileService.feed(user.getAccountId(), feed(dogId));

        Assert.assertEquals(20, profile.getAssets().getEnergyLimit());
        Assert.assertEquals(11, profile.getDogs().get(0).getEnergy());
    }

    @Test
    public void walkDogShouldConsumeEnergyAndGrantOutingBondOncePerDay() {
        User user = accountUser(990016L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "散步狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetProfileDTO afterWalk = PetProfileService.walkDog(user.getAccountId(), walkDog(dogId));

        PetDogDTO walkedDog = afterWalk.getDogs().get(0);
        Assert.assertEquals(11, walkedDog.getBond());
        Assert.assertEquals(9, walkedDog.getEnergy());

        PetProfileDTO afterRepeatWalk = PetProfileService.walkDog(user.getAccountId(), walkDog(dogId));
        PetDogDTO repeatedDog = afterRepeatWalk.getDogs().get(0);
        Assert.assertEquals(11, repeatedDog.getBond());
        Assert.assertEquals(9, repeatedDog.getEnergy());
    }

    @Test
    public void gameTrainingShouldGrantCompanionBondWithoutStatsOrEnergyCost() {
        User user = accountUser(990010L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "陪玩狗"));
        PetDogDTO before = profile.getDogs().get(0);

        PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        PetProfileDTO afterThirdWin = PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);

        PetDogDTO dog = afterThirdWin.getDogs().get(0);
        Assert.assertEquals(before.getWisdom(), dog.getWisdom());
        Assert.assertEquals(before.getEnergy(), dog.getEnergy());
        Assert.assertEquals(before.getBond() + 1, dog.getBond());
    }

    @Test
    public void gameTrainingShouldGrantCompanionBondOnlyOncePerDogPerDay() {
        User user = accountUser(990011L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "陪玩上限狗"));
        int beforeBond = profile.getDogs().get(0).getBond();

        for (int i = 0; i < 6; i++) {
            PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        }

        PetDogDTO dog = PetService.profile(user).getDogs().get(0);
        Assert.assertEquals(beforeBond + 1, dog.getBond());
    }

    @Test
    public void miniGameResultShouldApplyDailyRewardsAndCompanionBond() {
        User user = accountUser(990012L);
        PetProfileDTO before = PetService.adopt(user, adopt("corgi", "小游戏狗"));

        PetProfileDTO profile = PetService.applyMiniGameResult(user.getAccountId(), Game.GOBANG, true, 60);

        Assert.assertEquals(330, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(before.getDogs().get(0).getEnergy(), profile.getDogs().get(0).getEnergy());
        Assert.assertEquals(before.getDogs().get(0).getWisdom(), profile.getDogs().get(0).getWisdom());
        Assert.assertEquals(before.getDogs().get(0).getBond() + 1, profile.getDogs().get(0).getBond());
    }

    @Test
    public void miniGameResultShouldRespectDailyRewardLimits() {
        User user = accountUser(990013L);
        PetProfileDTO before = PetService.adopt(user, adopt("corgi", "小游戏上限狗"));

        PetProfileDTO profile = null;
        for (int i = 0; i < 6; i++) {
            profile = PetService.applyMiniGameResult(user.getAccountId(), Game.GOBANG, true, 60);
        }

        Assert.assertNotNull(profile);
        Assert.assertEquals(410, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(before.getDogs().get(0).getEnergy(), profile.getDogs().get(0).getEnergy());
        Assert.assertEquals(before.getDogs().get(0).getBond() + 1, profile.getDogs().get(0).getBond());
    }

    @Test
    public void profileShouldSerializeExpiredEnergyRefreshForSameAccount() throws Exception {
        User user = accountUser(990009L);
        PetService.adopt(user, adopt("corgi", "并发狗"));
        expireDogEnergy(user.getAccountId());

        int threadCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<PetProfileDTO> profiles = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    profiles.add(PetService.profile(user));
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
        }

        Assert.assertTrue("并发任务应全部就绪", ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        Assert.assertTrue("并发 profile 请求应及时完成", executor.awaitTermination(10, TimeUnit.SECONDS));

        Assert.assertTrue("同账号并发 profile 不应抛出 SQLITE_BUSY: " + failures, failures.isEmpty());
        Assert.assertEquals(threadCount, profiles.size());
        for (PetProfileDTO profile : profiles) {
            Assert.assertEquals(10, profile.getDogs().get(0).getEnergy());
        }
    }

    private static User accountUser(long accountId) {
        User user = new User();
        user.setId("test-" + accountId);
        user.setAccountId(accountId);
        user.setAccount("pet_tester_" + accountId);
        user.setNickname("测试员");
        user.setGuest(false);
        return user;
    }

    private static PetAdoptDTO adopt(String breed, String name) {
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setBreed(breed);
        dto.setName(name);
        return dto;
    }

    private static PetRaceResultDTO raceResult(String dogId, int rank) {
        return raceResult(dogId, rank, 0);
    }

    private static PetRaceResultDTO raceResult(String dogId, int rank, int weeklyPoints) {
        PetRaceResultDTO dto = new PetRaceResultDTO();
        dto.setDogId(dogId);
        dto.setRank(rank);
        dto.setWeeklyPoints(weeklyPoints);
        return dto;
    }

    private static PetWalkDogDTO walkDog(String dogId) {
        PetWalkDogDTO dto = new PetWalkDogDTO();
        dto.setDogId(dogId);
        return dto;
    }

    private static PetFeedDTO feed(String dogId) {
        PetFeedDTO dto = new PetFeedDTO();
        dto.setDogId(dogId);
        return dto;
    }

    private static PetShopBuyDTO shopBuy(String itemId, int quantity) {
        PetShopBuyDTO dto = new PetShopBuyDTO();
        dto.setItemId(itemId);
        dto.setQuantity(quantity);
        return dto;
    }

    private static void expireDogEnergy(long accountId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE dogs SET energy = 1, energy_date = '2000-01-01' WHERE owner_id = " + accountId);
        }
    }

    private static void updateDogStats(long accountId, String dogId, int speed, int stamina, int burst,
                                       int wisdom, int bond) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET speed = ?, stamina = ?, burst = ?, wisdom = ?, bond = ? WHERE owner_id = ? AND id = ?")) {
            statement.setInt(1, speed);
            statement.setInt(2, stamina);
            statement.setInt(3, burst);
            statement.setInt(4, wisdom);
            statement.setInt(5, bond);
            statement.setLong(6, accountId);
            statement.setString(7, dogId);
            statement.executeUpdate();
        }
    }

    private static void addTreasureMapFragments(long accountId, int count) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, 'treasure_map_fragment', ?, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = excluded.count, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            statement.setLong(1, accountId);
            statement.setInt(2, count);
            statement.setLong(3, now);
            statement.executeUpdate();
        }
    }

    private static void markMysteryCaveCompleted(long accountId) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, 'mystery_cave_completed', 1, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = 1, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            statement.setLong(1, accountId);
            statement.setLong(2, now);
            statement.executeUpdate();
        }
    }

    private static void insertBackHillCollections(long accountId) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, 1, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = 1, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            for (String itemId : new String[]{
                    "back_hill_ball",
                    "back_hill_branch",
                    "back_hill_leaf",
                    "back_hill_stone",
                    "back_hill_mushroom",
                    "back_hill_feather"
            }) {
                statement.setLong(1, accountId);
                statement.setString(2, itemId);
                statement.setLong(3, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertCreekCollections(long accountId) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, 1, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = 1, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            for (String itemId : new String[]{
                    "creek_shell",
                    "creek_snail",
                    "creek_lotus",
                    "creek_duck",
                    "creek_coral",
                    "creek_drop"
            }) {
                statement.setLong(1, accountId);
                statement.setString(2, itemId);
                statement.setLong(3, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSnowMountainCollections(long accountId) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, 1, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = 1, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            for (String itemId : new String[]{
                    "snow_mountain_snowflake",
                    "snow_mountain_ice",
                    "snow_mountain_skate",
                    "snow_mountain_cloud",
                    "snow_mountain_board",
                    "snow_mountain_deer"
            }) {
                statement.setLong(1, accountId);
                statement.setString(2, itemId);
                statement.setLong(3, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setDogEnergy(long accountId, String dogId, int energy, String energyDate) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET energy = ?, energy_date = ? WHERE owner_id = ? AND id = ?")) {
            statement.setInt(1, energy);
            statement.setString(2, energyDate);
            statement.setLong(3, accountId);
            statement.setString(4, dogId);
            statement.executeUpdate();
        }
    }

    private static void insertCheckins(long accountId, int count) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_checkins (account_id, checkin_date, cycle_day, created_at) " +
                             "VALUES (?, ?, ?, ?)")) {
            java.time.LocalDate startDate = java.time.LocalDate.of(2020, 1, 1);
            for (int i = 0; i < count; i++) {
                statement.setLong(1, accountId);
                statement.setString(2, startDate.plusDays(i).toString());
                statement.setInt(3, i % 7 + 1);
                statement.setLong(4, now + i);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static int findCollectionCount(long accountId, String itemId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT count FROM pet_collections WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int findItemCount(long accountId, String itemId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT count FROM pet_items WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }
}
