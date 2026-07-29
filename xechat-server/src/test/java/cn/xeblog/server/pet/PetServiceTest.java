package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingAssignmentsDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentListDTO;
import cn.xeblog.commons.entity.pet.AdminReassignPetDailySayingDTO;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetCheckinMilestoneRewardDTO;
import cn.xeblog.commons.entity.pet.PetDailyCompanionDogStatusDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingReadDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetExploreChestDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenResultDTO;
import cn.xeblog.commons.entity.pet.PetExploreRewardDTO;
import cn.xeblog.commons.entity.pet.PetExploreStartDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillActionDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDefinitionDTO;
import cn.xeblog.commons.entity.pet.PetUseItemDTO;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

public class PetServiceTest {

    private static final int PUBLIC_DOG_ADOPTION_PRICE = 750;

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
        Assert.assertEquals(10, profile.getAssets().getEnergy());
        Assert.assertEquals(LocalDate.now().toString(), profile.getAssets().getEnergyDate());
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertNotNull(profile.getCheckinStatus().getServerDate());
        Assert.assertFalse(profile.isShibaUnlockCelebrationPending());
    }

    @Test
    public void petDogDtoShouldNotExposeDeprecatedStatsOrEnergy() throws Exception {
        List<String> fieldNames = new ArrayList<>();
        for (java.lang.reflect.Field field : PetDogDTO.class.getDeclaredFields()) {
            fieldNames.add(field.getName());
        }

        Assert.assertFalse(fieldNames.contains("speed"));
        Assert.assertFalse(fieldNames.contains("stamina"));
        Assert.assertFalse(fieldNames.contains("burst"));
        Assert.assertFalse(fieldNames.contains("wisdom"));
        Assert.assertFalse(fieldNames.contains("energy"));
    }

    @Test
    public void dailySayingShouldAllowSameMessageAcrossPlayersWhenPoolIsSmall() throws Exception {
        upsertDailySayingContent("shared-message", "今天也想陪{dog_name}慢慢走。");
        User first = accountUser(990101L);
        User second = accountUser(990102L);
        PetService.adopt(first, adopt("corgi", "小一"));
        PetService.adopt(second, adopt("corgi", "小二"));

        PetDailySayingDTO firstSaying = PetDailySayingService.dailySaying(first.getAccountId()).getDailySaying();
        PetDailySayingDTO secondSaying = PetDailySayingService.dailySaying(second.getAccountId()).getDailySaying();

        Assert.assertEquals("UNREAD", firstSaying.getState());
        Assert.assertEquals("UNREAD", secondSaying.getState());
        Assert.assertEquals("shared-message", firstSaying.getContent().getContentId());
        Assert.assertEquals("shared-message", secondSaying.getContent().getContentId());
    }

    @Test
    public void dailySayingShouldNotReuseSameMessageForSamePlayerAcrossDays() throws Exception {
        upsertDailySayingContent("same-message-a", "今天也想陪{dog_name}慢慢走。");
        upsertDailySayingContent("same-message-b", "今天也想陪{dog_name}慢慢走。");
        User user = accountUser(990103L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "小三"));
        PetDogDTO dog = profile.getDogs().get(0);
        insertDailySayingAssignment(user.getAccountId(), dog.getId(), dog.getName(), dog.getBreed(),
                "same-message-a", LocalDate.now().minusDays(1).toString());

        PetDailySayingDTO saying = PetDailySayingService.dailySaying(user.getAccountId()).getDailySaying();

        Assert.assertEquals("NONE", saying.getState());
    }

    @Test
    public void adminListDailySayingAssignmentsShouldExposeReadStatus() throws Exception {
        upsertDailySayingContent("admin-list-message", "今天也想陪{dog_name}晒太阳。");
        User user = accountUser(990104L);
        insertAccount(user);
        PetService.adopt(user, adopt("corgi", "问候狗"));

        PetDailySayingDTO unread = PetDailySayingService.dailySaying(user.getAccountId()).getDailySaying();
        AdminPetDailySayingAssignmentListDTO unreadList = PetDailySayingService.adminListAssignments(
                new AdminListPetDailySayingAssignmentsDTO(LocalDate.now().toString(), null, null, 1, 10));

        Assert.assertEquals(1, unreadList.getTotal());
        Assert.assertEquals("UNREAD", unreadList.getItems().get(0).getStatus());
        Assert.assertEquals(unread.getAssignmentId(), unreadList.getItems().get(0).getAssignmentId());

        PetDailySayingReadDTO readRequest = new PetDailySayingReadDTO();
        readRequest.setAssignmentId(unread.getAssignmentId());
        PetDailySayingService.readDailySaying(user.getAccountId(), readRequest);

        AdminPetDailySayingAssignmentListDTO readList = PetDailySayingService.adminListAssignments(
                new AdminListPetDailySayingAssignmentsDTO(LocalDate.now().toString(), null, "READ", 1, 10));
        AdminPetDailySayingAssignmentDTO read = readList.getItems().get(0);
        Assert.assertEquals("READ", read.getStatus());
        Assert.assertNotNull(read.getReadAt());
        Assert.assertEquals(LocalDate.now().toString(), read.getReadServerDate());
    }

    @Test
    public void adminReassignDailySayingShouldClearReadStateAndReplaceContent() throws Exception {
        upsertDailySayingContent("replace-message-a", "今天也想陪{dog_name}看云。");
        upsertDailySayingContent("replace-message-b", "今天也想陪{dog_name}吹风。");
        User user = accountUser(990105L);
        insertAccount(user);
        PetService.adopt(user, adopt("corgi", "换句狗"));
        PetDailySayingDTO original = PetDailySayingService.dailySaying(user.getAccountId()).getDailySaying();
        PetDailySayingReadDTO readRequest = new PetDailySayingReadDTO();
        readRequest.setAssignmentId(original.getAssignmentId());
        PetDailySayingService.readDailySaying(user.getAccountId(), readRequest);

        AdminPetDailySayingAssignmentListDTO reassigned = PetDailySayingService.adminReassign(
                new AdminReassignPetDailySayingDTO(original.getAssignmentId()));
        AdminPetDailySayingAssignmentDTO item = reassigned.getItems().get(0);

        Assert.assertEquals(original.getAssignmentId(), item.getAssignmentId());
        Assert.assertEquals("UNREAD", item.getStatus());
        Assert.assertNull(item.getReadAt());
        Assert.assertNull(item.getReadServerDate());
        Assert.assertNull(item.getGreetingRewardApplied());
        Assert.assertNull(item.getGreetingIntimacyDelta());
        Assert.assertNotEquals(original.getContent().getContentId(), item.getContentId());
    }

    @Test
    public void dailySayingReadShouldRewardBonesOnlyOnceAfterView() throws Exception {
        upsertDailySayingContent("view-reward-message", "今天也想陪{dog_name}晒太阳。");
        User user = accountUser(990106L);
        PetService.adopt(user, adopt("corgi", "奖励狗"));

        PetDailySayingDTO saying = PetDailySayingService.dailySaying(user.getAccountId()).getDailySaying();
        PetDailySayingReadDTO viewRequest = new PetDailySayingReadDTO();
        viewRequest.setAssignmentId(saying.getAssignmentId());

        PetProfileDTO viewed = PetDailySayingService.viewDailySaying(user.getAccountId(), viewRequest);
        PetProfileDTO viewedAgain = PetDailySayingService.viewDailySaying(user.getAccountId(), viewRequest);
        PetProfileDTO read = PetDailySayingService.readDailySaying(user.getAccountId(), viewRequest);
        PetProfileDTO readAgain = PetDailySayingService.readDailySaying(user.getAccountId(), viewRequest);

        Assert.assertEquals(300, viewed.getAssets().getBones());
        Assert.assertEquals(300, viewedAgain.getAssets().getBones());
        Assert.assertEquals(350, read.getAssets().getBones());
        Assert.assertEquals(350, readAgain.getAssets().getBones());
        Assert.assertEquals("UNREAD", viewedAgain.getDailySaying().getState());
        Assert.assertEquals("READ_TODAY", read.getDailySaying().getState());
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
    public void adoptShouldCreateFirstDogAndAllowKennelGrowthPastActivitySlots() throws Exception {
        User user = accountUser(990002L);

        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "小白"));

        Assert.assertEquals(1, profile.getDogs().size());
        Assert.assertEquals(300, profile.getAssets().getBones());
        PetDogDTO dog = profile.getDogs().get(0);
        Assert.assertEquals("小白", dog.getName());
        Assert.assertEquals("corgi", dog.getBreed());
        Assert.assertEquals("puppy", dog.getStage());
        Assert.assertEquals(0, dog.getWeeklyPoints());

        setAccountBones(user.getAccountId(), 1000);

        PetProfileDTO afterSecond = PetService.adopt(user, adopt("golden", "小黄"));

        Assert.assertEquals(1, afterSecond.getAssets().getDogSlots());
        Assert.assertEquals(1000 - PUBLIC_DOG_ADOPTION_PRICE, afterSecond.getAssets().getBones());
        Assert.assertEquals(2, afterSecond.getDogs().size());
        Assert.assertEquals("小黄", afterSecond.getDogs().get(1).getName());
        Assert.assertEquals("golden", afterSecond.getDogs().get(1).getBreed());
    }

    @Test
    public void adoptSecondPublicBreedRejectsWhenBonesAreNotEnough() {
        User user = accountUser(990017L);
        PetService.adopt(user, adopt("corgi", "小白"));

        try {
            PetService.adopt(user, adopt("golden", "小黄"));
            Assert.fail("已有狗狗后购买公开品种应校验骨头币");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("骨头币不足", e.getMessage());
        }

        PetProfileDTO profile = PetProfileService.profile(user.getAccountId());
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getDogs().size());
    }

    @Test
    public void adoptNativeShouldBeRejected() {
        User user = accountUser(990016L);

        try {
            PetService.adopt(user, adopt("native", "田园"));
            Assert.fail("中华田园犬已从 v5 品种池移除，不应允许新领养");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("该品种暂不可领养", e.getMessage());
        }
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
    public void adoptShibaAfterThirtyCheckins() throws Exception {
        User user = accountUser(990013L);
        PetService.adopt(user, adopt("corgi", "小白"));
        insertCheckins(user.getAccountId(), 29);
        PetProfileDTO unlockedProfile = PetProfileService.checkin(user.getAccountId());

        Assert.assertTrue(unlockedProfile.isShibaUnlockCelebrationPending());

        PetProfileDTO afterAdopt = PetService.adopt(user, adopt("shiba", "小柴"));

        Assert.assertEquals(2, afterAdopt.getDogs().size());
        PetDogDTO shiba = afterAdopt.getDogs().get(1);
        Assert.assertEquals("小柴", shiba.getName());
        Assert.assertEquals("shiba", shiba.getBreed());

        PetProfileDTO acknowledgedProfile =
                PetProfileService.acknowledgeShibaUnlockCelebration(user.getAccountId());
        Assert.assertFalse(acknowledgedProfile.isShibaUnlockCelebrationPending());
        Assert.assertFalse(PetProfileService.profile(user.getAccountId()).isShibaUnlockCelebrationPending());
    }

    @Test
    public void raceResultShouldAdvanceRaceCountersAndAdultStage() {
        User user = accountUser(990003L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "赛跑狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetService.applyRaceResult(user, raceResult(dogId, 2));
        PetService.applyRaceResult(user, raceResult(dogId, 2));
        PetProfileDTO afterThirdRace = PetService.applyRaceResult(user, raceResult(dogId, 1));

        PetDogDTO dog = afterThirdRace.getDogs().get(0);
        Assert.assertEquals(3, dog.getRaceCount());
        Assert.assertEquals(1, dog.getRaceFirstCount());
        Assert.assertTrue(afterThirdRace.getDailyCompanionStatus().getDogs().get(dogId).isOutingCompleted());
    }

    @Test
    public void raceSignupShouldSpendBonesAndDogEnergyAtomically() {
        User user = accountUser(990004L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "报名狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetProfileDTO afterSignup = PetService.spendRaceSignup(user.getAccountId(), dogId, 3, 20);

        Assert.assertEquals(280, afterSignup.getAssets().getBones());
        Assert.assertEquals(7, afterSignup.getAssets().getEnergy());
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
        Assert.assertEquals(1, afterFailure.getAssets().getEnergy());
    }

    @Test
    public void raceWeeklyPointsShouldAccumulateOnDog() {
        User user = accountUser(990006L);
        PetProfileDTO profile = PetService.adopt(user, adopt("golden", "周榜狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetService.applyRaceResult(user, raceResult(dogId, 2, 6));
        PetProfileDTO afterSecond = PetService.applyRaceResult(user, raceResult(dogId, 1, 10));

        Assert.assertEquals(16, afterSecond.getDogs().get(0).getWeeklyPoints());
    }

    @Test
    public void profileShouldClampDogBondToDesignRange() throws Exception {
        User user = accountUser(990007L);
        PetProfileDTO profile = PetService.adopt(user, adopt("poodle", "上限狗"));
        String dogId = profile.getDogs().get(0).getId();
        updateDogBond(user.getAccountId(), dogId, 130);

        PetDogDTO dog = PetService.profile(user).getDogs().get(0);

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
    public void checkinShouldGrantMilestoneSkinFirstEvery28Checkins() throws Exception {
        User user = accountUser(990017L);
        PetService.profile(user);
        insertCheckins(user.getAccountId(), 27);

        PetProfileDTO profile = PetProfileService.checkin(user.getAccountId());

        PetCheckinMilestoneRewardDTO reward = profile.getCheckinStatus().getLastMilestoneReward();
        Assert.assertNotNull(reward);
        Assert.assertEquals(1, reward.getMilestoneIndex());
        Assert.assertNull(reward.getDecorationId());
        Assert.assertTrue(PetItemDefinitions.skinItemIds().contains(reward.getItemId()));
        Assert.assertEquals(0, reward.getOverflowBones());
        Assert.assertEquals(28, profile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(28, profile.getCheckinStatus().getMilestoneRemaining());
        Assert.assertEquals(0, findCollectionCount(user.getAccountId(), "checkin_decoration_hat"));
        Assert.assertEquals(1, findItemCount(user.getAccountId(), reward.getItemId()));
        Assert.assertEquals(1, countItemLedger(user.getAccountId(), reward.getItemId(),
                "gain", "checkin_reward"));
    }

    @Test
    public void seventhDayCheckinShouldGrantOneNormalItem() throws Exception {
        User user = accountUser(990020L);
        PetProfileDTO beforeProfile = PetService.profile(user);
        insertCheckins(user.getAccountId(), 6);

        PetProfileDTO profile = PetProfileService.checkin(user.getAccountId());

        int normalItemCount = 0;
        for (String itemId : PetItemDefinitions.luckyBagNormalItemIds()) {
            normalItemCount += findItemCount(user.getAccountId(), itemId);
        }
        Assert.assertEquals(beforeProfile.getAssets().getBones() + 100, profile.getAssets().getBones());
        Assert.assertEquals(1, normalItemCount);
        Assert.assertEquals(7, profile.getCheckinStatus().getCycleDay());
        Assert.assertEquals(7, profile.getCheckinStatus().getTotalCheckins());
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
        setAccountEnergy(user.getAccountId(), 1, "2000-01-01");

        PetProfileDTO profile = PetService.profile(user);

        Assert.assertEquals(20, profile.getAssets().getEnergyLimit());
        Assert.assertEquals(20, profile.getAssets().getEnergy());
    }

    @Test
    public void feedShouldRestoreEnergyUpToSnowMountainCollectionLimit() throws Exception {
        User user = accountUser(990020L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "雪山饭狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertSnowMountainCollections(user.getAccountId());
        setAccountEnergy(user.getAccountId(), 10, LocalDate.now().toString());

        PetProfileDTO profile = PetProfileService.feed(user.getAccountId(), feed(dogId));

        Assert.assertEquals(20, profile.getAssets().getEnergyLimit());
        Assert.assertEquals(11, profile.getAssets().getEnergy());
    }

    @Test
    public void endedExploreShouldStoreChestInstanceWithSnapshotAndLedger() throws Exception {
        User user = accountUser(990027L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "实例狗"));
        String dogId = adopted.getDogs().get(0).getId();
        PetProfileService.trainingLearn(user.getAccountId(), trainingSkill("explore_bones", null));
        PetProfileService.trainingEquip(user.getAccountId(), trainingSkill("explore_bones", dogId));

        PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 4));
        setExploreEnded(user.getAccountId(), dogId);

        PetProfileDTO settled = PetProfileService.profile(user.getAccountId());

        Assert.assertEquals(1, settled.getExploreChests().size());
        PetExploreChestDTO chest = settled.getExploreChests().get(0);
        Assert.assertEquals("chest_back_hill", chest.getChestItemId());
        Assert.assertEquals("back_hill", chest.getLocation());
        Assert.assertEquals(dogId, chest.getSourceDogId());
        Assert.assertEquals("实例狗", chest.getSourceDogName());
        Assert.assertEquals("corgi", chest.getSourceDogBreed());
        Assert.assertEquals(4, chest.getDurationHours());
        Assert.assertEquals("explore_bones", chest.getSkillSnapshotId());
        Assert.assertEquals(Integer.valueOf(1), chest.getSkillSnapshotLevel());
        Assert.assertEquals("v5-explore-training", chest.getSkillSnapshotDefinitionVersion());
        Assert.assertTrue(settled.getDailyCompanionStatus().getDogs().get(dogId).isOutingCompleted());
        Assert.assertEquals(0, findItemCount(user.getAccountId(), "chest_back_hill"));
        Assert.assertEquals(1, countItemLedger(user.getAccountId(), "chest_back_hill",
                "gain", "explore_return_chest"));
    }

    @Test
    public void exploreCancelShouldResetDogWithoutChestReward() throws Exception {
        User user = accountUser(990034L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "取消狗"));
        String dogId = adopted.getDogs().get(0).getId();

        PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 1));
        PetProfileDTO canceled = PetProfileService.exploreCancel(user.getAccountId(), exploreOpen(dogId));

        PetDogDTO dog = canceled.getDogs().stream()
                .filter(item -> dogId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(dog);
        Assert.assertEquals("idle", dog.getStatus());
        Assert.assertNull(dog.getExploreLocation());
        Assert.assertNull(dog.getExploreEndsAt());
        Assert.assertEquals(8, canceled.getAssets().getEnergy());
        Assert.assertTrue(canceled.getExploreChests().isEmpty());
        Assert.assertEquals(0, findItemCount(user.getAccountId(), "chest_back_hill"));
        Assert.assertEquals(0, countItemLedger(user.getAccountId(), "chest_back_hill",
                "gain", "explore_return_chest"));
    }

    @Test
    public void creekExploreShouldRequireDrawGuessWinsOrTacitQuizSameAnswers() {
        User blocked = accountUser(990038L);
        PetProfileDTO blockedProfile = PetService.adopt(blocked, adopt("corgi", "小溪门禁狗"));
        applyMiniGameResults(blocked.getAccountId(), Game.DRAW_GUESS, true, 9);
        recordTacitQuizSameAnswers(blocked.getAccountId(), 49);
        try {
            PetProfileService.exploreStart(blocked.getAccountId(),
                    exploreStart(blockedProfile.getDogs().get(0).getId(), "creek", 1));
            Assert.fail("你画我猜未满 10 次且默契问答答案相同未满 50 次时不能进入小溪");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("你画我猜胜利 10 次或默契问答答案相同 50 次"));
        }

        User drawGuess = accountUser(990039L);
        PetProfileDTO drawProfile = PetService.adopt(drawGuess, adopt("corgi", "画猜狗"));
        applyMiniGameResults(drawGuess.getAccountId(), Game.DRAW_GUESS, true, 10);
        PetProfileDTO drawStarted = PetProfileService.exploreStart(drawGuess.getAccountId(),
                exploreStart(drawProfile.getDogs().get(0).getId(), "creek", 1));
        Assert.assertEquals("creek", drawStarted.getDogs().get(0).getExploreLocation());

        User tacitQuiz = accountUser(990040L);
        PetProfileDTO tacitProfile = PetService.adopt(tacitQuiz, adopt("corgi", "默契狗"));
        recordTacitQuizSameAnswers(tacitQuiz.getAccountId(), 50);
        PetProfileDTO tacitStarted = PetProfileService.exploreStart(tacitQuiz.getAccountId(),
                exploreStart(tacitProfile.getDogs().get(0).getId(), "creek", 1));
        Assert.assertEquals(50, tacitStarted.getExploreStatus().getTacitQuizSameAnswers());
        Assert.assertEquals("creek", tacitStarted.getDogs().get(0).getExploreLocation());
    }

    @Test
    public void tacitQuizSameAnswerProgressShouldUnlockCreekWithoutMiniGameRewards() {
        User user = accountUser(990041L);
        PetProfileDTO before = PetService.adopt(user, adopt("corgi", "默契进度狗"));
        String dogId = before.getDogs().get(0).getId();

        recordTacitQuizSameAnswers(user.getAccountId(), 50);

        PetProfileDTO profile = PetService.profile(user);
        Assert.assertEquals(50, profile.getExploreStatus().getTacitQuizSameAnswers());
        Assert.assertEquals(before.getAssets().getBones(), profile.getAssets().getBones());
        Assert.assertEquals(before.getAssets().getMakeupCards(), profile.getAssets().getMakeupCards());

        PetProfileDTO started = PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "creek", 1));
        Assert.assertEquals("creek", started.getDogs().get(0).getExploreLocation());
    }

    @Test
    public void openChestInstanceShouldUseStoredDurationSkillSnapshotAndLedger() throws Exception {
        User user = accountUser(990028L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "开箱狗"));
        String dogId = adopted.getDogs().get(0).getId();
        PetProfileService.trainingLearn(user.getAccountId(), trainingSkill("explore_bones", null));
        PetProfileService.trainingEquip(user.getAccountId(), trainingSkill("explore_bones", dogId));
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 4));
            setExploreEnded(user.getAccountId(), dogId);
            PetProfileDTO settled = PetProfileService.profile(user.getAccountId());
            String chestId = settled.getExploreChests().get(0).getId();

            PetExploreOpenResultDTO result = PetProfileService.openBackHillChest(
                    user.getAccountId(), useChest("chest_back_hill", chestId));

            Assert.assertTrue(result.getRewards().stream()
                    .anyMatch(reward -> "bones".equals(reward.getType())
                            && reward.getAmount() == 27));
            Assert.assertTrue(result.getProfile().getExploreChests().isEmpty());
            Assert.assertEquals(1, countItemLedger(user.getAccountId(), "chest_back_hill",
                    "spend", "open_explore_chest"));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void openLegacyChestsShouldAllowBatchQuantity() throws Exception {
        User user = accountUser(990037L);
        PetService.profile(user);
        insertItem(user.getAccountId(), "chest_back_hill", 2);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);

        try {
            PetExploreOpenResultDTO result = PetProfileService.openBackHillChest(
                    user.getAccountId(), useChest("chest_back_hill", null, 2));

            long boneRewardCount = result.getRewards().stream()
                    .filter(reward -> "bones".equals(reward.getType()))
                    .count();
            Assert.assertTrue(boneRewardCount >= 2L);
            Assert.assertEquals(0, findItemCount(user.getAccountId(), "chest_back_hill"));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void buyingNormalItemShouldRecordItemLedger() throws Exception {
        User user = accountUser(990029L);
        String itemId = PetProfileService.profile(user.getAccountId()).getShopStatus().getNormalItemIds().get(0);

        PetProfileService.shopBuy(user.getAccountId(), shopBuy(itemId, 2));

        Assert.assertEquals(2, findItemCount(user.getAccountId(), itemId));
        Assert.assertEquals(1, countItemLedger(user.getAccountId(), itemId, "gain", "shop_buy_normal"));
    }

    @Test
    public void exploreOpenShouldApplyOldLibraryCollectionBonesBonus() throws Exception {
        User user = accountUser(990021L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "书馆狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertOldLibraryCollections(user.getAccountId());
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 1));
            setExploreEnded(user.getAccountId(), dogId);

            PetExploreOpenResultDTO result = PetProfileService.exploreOpen(user.getAccountId(), exploreOpen(dogId));

            Assert.assertEquals("bones", result.getRewards().get(0).getType());
            Assert.assertEquals(11, result.getRewards().get(0).getAmount());
            Assert.assertEquals(326, result.getProfile().getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void exploreOpenShouldGrantEasterSnailCollection() throws Exception {
        User user = accountUser(990023L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "蜗牛狗"));
        String dogId = adopted.getDogs().get(0).getId();
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 79);
        IntSupplier originalEasterSupplier = setExploreEasterEventSupplier(() -> 2);
        try {
            PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 1));
            setExploreEnded(user.getAccountId(), dogId);

            PetExploreOpenResultDTO result = PetProfileService.exploreOpen(user.getAccountId(), exploreOpen(dogId));

            Assert.assertTrue(result.getRewards().stream()
                    .anyMatch(reward -> "collection".equals(reward.getType())
                            && "easter_snail".equals(reward.getItemId())
                            && reward.getAmount() == 1));
            Assert.assertEquals(1, findCollectionCount(user.getAccountId(), "easter_snail"));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
            setExploreEasterEventSupplier(originalEasterSupplier);
        }
    }

    @Test
    public void checkinShouldConsumeNeighborSlipperAndGrantBonusBones() throws Exception {
        User user = accountUser(990024L);
        PetProfileDTO beforeProfile = PetService.profile(user);
        insertCollection(user.getAccountId(), "easter_neighbor_slipper", 1);

        PetProfileDTO afterProfile = PetProfileService.checkin(user.getAccountId());

        Assert.assertEquals(beforeProfile.getAssets().getBones() + 70, afterProfile.getAssets().getBones());
        Assert.assertEquals(0, findCollectionCount(user.getAccountId(), "easter_neighbor_slipper"));
    }

    @Test
    public void exploreOpenShouldConvertEasterEventToBonesWhenKnownEventsExhausted() throws Exception {
        User user = accountUser(990025L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "彩蛋满狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertCollection(user.getAccountId(), "easter_neighbor_slipper", 0);
        insertCollection(user.getAccountId(), "treasure_map_fragment", 3);
        insertCollection(user.getAccountId(), "easter_snail", 1);
        insertCollection(user.getAccountId(), "easter_visit_dog_tag", 0);
        insertCollection(user.getAccountId(), "easter_old_tennis", 1);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 79);
        try {
            PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 1));
            setExploreEnded(user.getAccountId(), dogId);

            PetExploreOpenResultDTO result = PetProfileService.exploreOpen(user.getAccountId(), exploreOpen(dogId));

            Assert.assertTrue(result.getRewards().stream()
                    .anyMatch(reward -> "bones".equals(reward.getType())
                            && reward.getItemId() == null
                            && reward.getAmount() == 50));
            Assert.assertEquals(360, result.getProfile().getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void exploreOpenShouldGrantVisitDogTagEasterEvent() throws Exception {
        User user = accountUser(990026L);
        PetProfileDTO adopted = PetService.adopt(user, adopt("corgi", "狗牌狗"));
        String dogId = adopted.getDogs().get(0).getId();
        insertCollection(user.getAccountId(), "easter_neighbor_slipper", 0);
        insertCollection(user.getAccountId(), "treasure_map_fragment", 3);
        insertCollection(user.getAccountId(), "easter_snail", 1);
        insertCollection(user.getAccountId(), "easter_old_tennis", 1);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 79);
        try {
            PetProfileService.exploreStart(user.getAccountId(), exploreStart(dogId, "back_hill", 1));
            setExploreEnded(user.getAccountId(), dogId);

            PetExploreOpenResultDTO result = PetProfileService.exploreOpen(user.getAccountId(), exploreOpen(dogId));

            Assert.assertTrue(result.getRewards().stream()
                    .anyMatch(reward -> "collection".equals(reward.getType())
                            && "easter_visit_dog_tag".equals(reward.getItemId())
                            && reward.getAmount() == 1));
            Assert.assertEquals(1, findCollectionCount(user.getAccountId(), "easter_visit_dog_tag"));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void walkDogShouldConsumeEnergyAndGrantOutingBondOncePerDay() {
        User user = accountUser(990016L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "散步狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetProfileDTO afterWalk = PetProfileService.walkDog(user.getAccountId(), walkDog(dogId));

        PetDogDTO walkedDog = afterWalk.getDogs().get(0);
        Assert.assertEquals(11, walkedDog.getBond());
        Assert.assertEquals(9, afterWalk.getAssets().getEnergy());

        PetProfileDTO afterRepeatWalk = PetProfileService.walkDog(user.getAccountId(), walkDog(dogId));
        PetDogDTO repeatedDog = afterRepeatWalk.getDogs().get(0);
        Assert.assertEquals(11, repeatedDog.getBond());
        Assert.assertEquals(9, afterRepeatWalk.getAssets().getEnergy());
    }

    @Test
    public void dailyCompanionStatusShouldTrackCareActions() {
        User user = accountUser(990053L);
        PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "陪伴状态狗"));
        String dogId = profile.getDogs().get(0).getId();

        PetDailyCompanionDogStatusDTO initialStatus = profile.getDailyCompanionStatus().getDogs().get(dogId);
        Assert.assertFalse(initialStatus.isGreetCompleted());
        Assert.assertFalse(initialStatus.isFeedCompleted());
        Assert.assertFalse(initialStatus.isPlayCompleted());
        Assert.assertFalse(initialStatus.isOutingCompleted());
        Assert.assertEquals(0, initialStatus.getCompletedCount());
        Assert.assertEquals(4, initialStatus.getTotalCount());

        PetProfileDTO greeted = PetProfileService.greetAllDogs(user.getAccountId());
        PetDailyCompanionDogStatusDTO greetedStatus = greeted.getDailyCompanionStatus().getDogs().get(dogId);
        Assert.assertTrue(greetedStatus.isGreetCompleted());
        Assert.assertEquals(1, greetedStatus.getCompletedCount());

        PetProfileDTO fed = PetProfileService.feed(user.getAccountId(), feed(dogId));
        PetDailyCompanionDogStatusDTO fedStatus = fed.getDailyCompanionStatus().getDogs().get(dogId);
        Assert.assertTrue(fedStatus.isFeedCompleted());
        Assert.assertEquals(2, fedStatus.getCompletedCount());

        PetProfileDTO played = PetService.applyGameTraining(user.getAccountId(), Game.GOBANG, true);
        PetDailyCompanionDogStatusDTO playedStatus = played.getDailyCompanionStatus().getDogs().get(dogId);
        Assert.assertTrue(playedStatus.isPlayCompleted());
        Assert.assertEquals(3, playedStatus.getCompletedCount());

        PetProfileDTO walked = PetProfileService.walkDog(user.getAccountId(), walkDog(dogId));
        PetDailyCompanionDogStatusDTO walkedStatus = walked.getDailyCompanionStatus().getDogs().get(dogId);
        Assert.assertTrue(walkedStatus.isOutingCompleted());
        Assert.assertEquals(4, walkedStatus.getCompletedCount());
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
        Assert.assertEquals(profile.getAssets().getEnergy(), afterThirdWin.getAssets().getEnergy());
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
        Assert.assertEquals(before.getAssets().getEnergy(), profile.getAssets().getEnergy());
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
        Assert.assertEquals(before.getAssets().getEnergy(), profile.getAssets().getEnergy());
        Assert.assertEquals(before.getDogs().get(0).getBond() + 1, profile.getDogs().get(0).getBond());
    }

    @Test
    public void miniGameRoomBonusShouldConsumeVisitDogTagAndRewardBothPlayers() throws Exception {
        User tagOwner = accountUser(990030L);
        User partner = accountUser(990031L);
        PetService.profile(tagOwner);
        PetService.profile(partner);
        insertCollection(tagOwner.getAccountId(), "easter_visit_dog_tag", 1);

        PetService.applyMiniGameRoomBonus(Game.GOBANG,
                Arrays.asList(tagOwner.getAccountId(), partner.getAccountId()), 60);

        Assert.assertEquals(310, PetService.profile(tagOwner).getAssets().getBones());
        Assert.assertEquals(310, PetService.profile(partner).getAssets().getBones());
        Assert.assertEquals(0, findCollectionCount(tagOwner.getAccountId(), "easter_visit_dog_tag"));
    }

    @Test
    public void miniGameRoomBonusShouldIgnoreDogBattleAndRace() throws Exception {
        User tagOwner = accountUser(990032L);
        User partner = accountUser(990033L);
        PetService.profile(tagOwner);
        PetService.profile(partner);
        insertCollection(tagOwner.getAccountId(), "easter_visit_dog_tag", 1);

        PetService.applyMiniGameRoomBonus(Game.DOG_BATTLE,
                Arrays.asList(tagOwner.getAccountId(), partner.getAccountId()), 60);
        PetService.applyMiniGameRoomBonus(Game.DOG_RACE,
                Arrays.asList(tagOwner.getAccountId(), partner.getAccountId()), 60);

        Assert.assertEquals(300, PetService.profile(tagOwner).getAssets().getBones());
        Assert.assertEquals(300, PetService.profile(partner).getAssets().getBones());
        Assert.assertEquals(1, findCollectionCount(tagOwner.getAccountId(), "easter_visit_dog_tag"));
    }

    @Test
    public void miniGameRoomBonusShouldRequireTwoPlayersAndEnoughDuration() throws Exception {
        User tagOwner = accountUser(990034L);
        User partner = accountUser(990035L);
        User third = accountUser(990036L);
        PetService.profile(tagOwner);
        PetService.profile(partner);
        PetService.profile(third);
        insertCollection(tagOwner.getAccountId(), "easter_visit_dog_tag", 1);

        PetService.applyMiniGameRoomBonus(Game.GOBANG,
                Arrays.asList(tagOwner.getAccountId(), partner.getAccountId(), third.getAccountId()), 60);
        PetService.applyMiniGameRoomBonus(Game.GOBANG,
                Arrays.asList(tagOwner.getAccountId(), partner.getAccountId()), 59);

        Assert.assertEquals(300, PetService.profile(tagOwner).getAssets().getBones());
        Assert.assertEquals(300, PetService.profile(partner).getAssets().getBones());
        Assert.assertEquals(300, PetService.profile(third).getAssets().getBones());
        Assert.assertEquals(1, findCollectionCount(tagOwner.getAccountId(), "easter_visit_dog_tag"));
    }

    @Test
    public void profileShouldSerializeExpiredEnergyRefreshForSameAccount() throws Exception {
        User user = accountUser(990009L);
        PetService.adopt(user, adopt("corgi", "并发狗"));
        expireAccountEnergy(user.getAccountId());

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
            Assert.assertEquals(10, profile.getAssets().getEnergy());
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

    private static PetExploreStartDTO exploreStart(String dogId, String location, int durationHours) {
        PetExploreStartDTO dto = new PetExploreStartDTO();
        dto.setDogId(dogId);
        dto.setLocation(location);
        dto.setDurationHours(durationHours);
        return dto;
    }

    private static PetExploreOpenDTO exploreOpen(String dogId) {
        PetExploreOpenDTO dto = new PetExploreOpenDTO();
        dto.setDogId(dogId);
        return dto;
    }

    private static PetUseItemDTO useItem(String itemId, String dogId) {
        PetUseItemDTO dto = new PetUseItemDTO();
        dto.setItemId(itemId);
        dto.setDogId(dogId);
        dto.setQuantity(1);
        return dto;
    }

    private static PetUseItemDTO useChest(String itemId, String chestId) {
        return useChest(itemId, chestId, 1);
    }

    private static PetUseItemDTO useChest(String itemId, String chestId, int quantity) {
        PetUseItemDTO dto = new PetUseItemDTO();
        dto.setItemId(itemId);
        dto.setChestId(chestId);
        dto.setQuantity(quantity);
        return dto;
    }

    private static PetTrainingSkillActionDTO trainingSkill(String skillId, String dogId) {
        PetTrainingSkillActionDTO dto = new PetTrainingSkillActionDTO();
        dto.setSkillId(skillId);
        dto.setDogId(dogId);
        return dto;
    }

    private static PetShopBuyDTO shopBuy(String itemId, int quantity) {
        PetShopBuyDTO dto = new PetShopBuyDTO();
        dto.setItemId(itemId);
        dto.setQuantity(quantity);
        return dto;
    }

    private static void applyMiniGameResults(long accountId, Game game, boolean win, int count) {
        for (int i = 0; i < count; i++) {
            PetService.applyMiniGameResult(accountId, game, win, 60);
        }
    }

    private static void recordTacitQuizSameAnswers(long accountId, int count) {
        for (int i = 0; i < count; i++) {
            PetService.recordTacitQuizSameAnswer(accountId);
        }
    }

    private static void expireAccountEnergy(long accountId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE pet_assets SET energy = 1, energy_date = '2000-01-01' WHERE account_id = " + accountId);
        }
    }

    private static void updateDogBond(long accountId, String dogId, int bond) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET bond = ? WHERE owner_id = ? AND id = ?")) {
            statement.setInt(1, bond);
            statement.setLong(2, accountId);
            statement.setString(3, dogId);
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

    private static void insertOldLibraryCollections(long accountId) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, 1, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = 1, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            for (String itemId : new String[]{
                    "old_library_scroll",
                    "old_library_pen",
                    "old_library_key",
                    "old_library_candle",
                    "old_library_book",
                    "old_library_bookmark"
            }) {
                statement.setLong(1, accountId);
                statement.setString(2, itemId);
                statement.setLong(3, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertCollection(long accountId, String itemId, int count) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, ?, 1, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = excluded.count, " +
                             "discovered = 1, updated_at = excluded.updated_at")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static void setAccountEnergy(long accountId, int energy, String energyDate) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET energy = ?, energy_date = ? WHERE account_id = ?")) {
            statement.setInt(1, energy);
            statement.setString(2, energyDate);
            statement.setLong(3, accountId);
            statement.executeUpdate();
        }
    }

    private static void setAccountBones(long accountId, int bones) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET bones = ? WHERE account_id = ?")) {
            statement.setInt(1, bones);
            statement.setLong(2, accountId);
            Assert.assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertAccount(User user) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO accounts " +
                             "(account_id, account, nickname, password_hash, avatar_version, role, permit, " +
                             "stealth, status, created_at, created_ip) " +
                             "VALUES (?, ?, ?, 'test-hash', 0, 'USER', 0, 0, 'ACTIVE', ?, '127.0.0.1')")) {
            statement.setLong(1, user.getAccountId());
            statement.setString(2, user.getAccount());
            statement.setString(3, user.getNickname() + user.getAccountId());
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static void upsertDailySayingContent(String contentId, String primaryText) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_daily_saying_contents " +
                             "(content_id, category, subtype, title, primary_text, recommended_weight, " +
                             "review_status, active, content_version, created_at, updated_at) " +
                             "VALUES (?, '温柔短句', '测试', '测试问候', ?, 1, '可发布', 1, 'test-version', ?, ?) " +
                             "ON CONFLICT(content_id) DO UPDATE SET primary_text = excluded.primary_text, " +
                             "review_status = '可发布', active = 1, updated_at = excluded.updated_at")) {
            statement.setString(1, contentId);
            statement.setString(2, primaryText);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static void insertDailySayingAssignment(long accountId, String dogId, String dogName, String dogBreed,
                                                    String contentId, String assignedDate) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_daily_saying_assignments " +
                             "(assignment_id, account_id, dog_id, dog_name_snapshot, dog_avatar_snapshot, " +
                             "content_id, assigned_server_date, status, assigned_at, read_at, read_server_date, " +
                             "greeting_reward_applied, greeting_intimacy_delta, content_version) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, 'READ', ?, ?, ?, 0, 0, 'test-version')")) {
            statement.setString(1, "history-" + accountId + "-" + contentId);
            statement.setLong(2, accountId);
            statement.setString(3, dogId);
            statement.setString(4, dogName);
            statement.setString(5, dogBreed);
            statement.setString(6, contentId);
            statement.setString(7, assignedDate);
            statement.setLong(8, now - TimeUnit.DAYS.toMillis(1));
            statement.setLong(9, now - TimeUnit.DAYS.toMillis(1));
            statement.setString(10, assignedDate);
            statement.executeUpdate();
        }
    }

    private static void setExploreEnded(long accountId, String dogId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET explore_ends_at = ? WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, System.currentTimeMillis() - 1000L);
            statement.setLong(2, accountId);
            statement.setString(3, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertItem(long accountId, String itemId, int count) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_items (account_id, item_id, count, updated_at) VALUES (?, ?, ?, ?) " +
                             "ON CONFLICT(account_id, item_id) DO UPDATE SET count = excluded.count, " +
                             "updated_at = excluded.updated_at")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static int countItemLedger(long accountId, String itemId, String direction, String source) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM pet_item_ledger " +
                             "WHERE account_id = ? AND item_id = ? AND direction = ? AND source = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setString(3, direction);
            statement.setString(4, source);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static IntSupplier setExploreRollSupplier(IntSupplier supplier) throws Exception {
        Field field = PetProfileService.class.getDeclaredField("exploreRollSupplier");
        field.setAccessible(true);
        IntSupplier original = (IntSupplier) field.get(null);
        field.set(null, supplier);
        return original;
    }

    private static IntSupplier setExploreEasterEventSupplier(IntSupplier supplier) throws Exception {
        Field field = PetProfileService.class.getDeclaredField("exploreEasterEventSupplier");
        field.setAccessible(true);
        IntSupplier original = (IntSupplier) field.get(null);
        field.set(null, supplier);
        return original;
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
