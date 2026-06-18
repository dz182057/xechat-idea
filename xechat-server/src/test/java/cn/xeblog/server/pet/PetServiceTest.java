package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PetServiceTest {

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
    public void adoptShouldCreateFirstDogAndRespectSlotLimit() {
        User user = accountUser(990002L);

        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "小白"));

        Assert.assertEquals(1, profile.getDogs().size());
        PetDogDTO dog = profile.getDogs().get(0);
        Assert.assertEquals("小白", dog.getName());
        Assert.assertEquals("corgi", dog.getBreed());
        Assert.assertEquals("puppy", dog.getStage());
        Assert.assertEquals(10, dog.getEnergy());
        Assert.assertEquals(0, dog.getWeeklyPoints());

        try {
            PetService.adopt(user, adopt("golden", "小黄"));
            Assert.fail("狗位已满时不应继续领养");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("当前狗位已满", e.getMessage());
        }
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
    public void checkinShouldIncreaseCurrentDogBond() {
        User user = accountUser(990008L);
        PetProfileDTO profile = PetService.adopt(user, adopt("native", "签到狗"));
        int beforeBond = profile.getDogs().get(0).getBond();

        PetDogDTO dog = PetProfileService.checkin(user.getAccountId()).getDogs().get(0);

        Assert.assertEquals(beforeBond + 10, dog.getBond());
    }

    @Test
    public void gameTrainingShouldConvertCompanionExperienceAndSpendEnergy() {
        User user = accountUser(990010L);
        PetProfileDTO profile = PetService.adopt(user, adopt("native", "训练狗"));
        PetDogDTO before = profile.getDogs().get(0);

        PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        PetProfileDTO afterThirdWin = PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);

        PetDogDTO dog = afterThirdWin.getDogs().get(0);
        Assert.assertEquals(before.getWisdom() + 1, dog.getWisdom());
        Assert.assertEquals(before.getEnergy() - 3, dog.getEnergy());
    }

    @Test
    public void gameTrainingShouldRespectDailySingleStatLimit() {
        User user = accountUser(990011L);
        PetService.adopt(user, adopt("native", "训练上限狗"));

        for (int i = 0; i < 6; i++) {
            PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        }

        PetDogDTO dog = PetService.profile(user).getDogs().get(0);
        Assert.assertEquals(10, dog.getWisdom());
    }

    @Test
    public void miniGameResultShouldApplyDailyRewardsAndTraining() {
        User user = accountUser(990012L);
        PetService.adopt(user, adopt("native", "小游戏狗"));

        PetProfileDTO profile = PetService.applyMiniGameResult(user.getAccountId(), Game.GOBANG, true, 60);

        Assert.assertEquals(330, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(9, profile.getDogs().get(0).getEnergy());
    }

    @Test
    public void miniGameResultShouldRespectDailyRewardLimits() {
        User user = accountUser(990013L);
        PetService.adopt(user, adopt("native", "小游戏上限狗"));

        PetProfileDTO profile = null;
        for (int i = 0; i < 6; i++) {
            profile = PetService.applyMiniGameResult(user.getAccountId(), Game.GOBANG, true, 60);
        }

        Assert.assertNotNull(profile);
        Assert.assertEquals(410, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(5, profile.getDogs().get(0).getEnergy());
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

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }
}
