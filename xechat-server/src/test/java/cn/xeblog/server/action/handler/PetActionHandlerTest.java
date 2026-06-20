package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetCollectionItemDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenResultDTO;
import cn.xeblog.commons.entity.pet.PetExploreStatusDTO;
import cn.xeblog.commons.entity.pet.PetInventoryItemDTO;
import cn.xeblog.commons.entity.pet.PetInteractionStatusDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.entity.pet.PetRenameDTO;
import cn.xeblog.commons.entity.pet.PetResponseDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDefinitionDTO;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.PetAction;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.pet.PetProfileService;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

public class PetActionHandlerTest {

    private static final String BACK_HILL_CHEST_ITEM_ID = "chest_back_hill";
    private static final String CREEK_CHEST_ITEM_ID = "chest_creek";

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-pet-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();
    }

    @After
    public void tearDown() throws Exception {
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void petProfileReturnsAccountBoundEmptyProfile() {
        User user = new User();
        user.setId("desktop-channel");
        user.setAccountId(1001L);
        user.setAccount("dog_user");
        user.setNickname("养狗人");
        user.setStatus(UserStatus.FISHING);
        user.setChannel(new EmbeddedChannel());

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.PET_PROFILE);

        new PetActionHandler().process(user, request);

        Response response = ((EmbeddedChannel) user.getChannel()).readOutbound();
        Assert.assertEquals(MessageType.PET, response.getType());

        PetResponseDTO body = (PetResponseDTO) response.getBody();
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.PET_PROFILE, body.getPetAction());
        Assert.assertNull(body.getError());

        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(1001L, profile.getAccountId());
        Assert.assertEquals(0, profile.getDogs().size());
        Assert.assertNull(profile.getCompanionDogId());
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertEquals(LocalDate.now().toString(), profile.getCheckinStatus().getServerDate());
        Assert.assertFalse(profile.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(1, profile.getCheckinStatus().getCycleDay());
        Assert.assertTrue(profile.getCheckinStatus().getCheckedDatesInMonth().isEmpty());
        Assert.assertNotNull(profile.getExploreStatus());
        Assert.assertEquals(3, profile.getExploreStatus().getDailyStartLimit());
        Assert.assertEquals(0, profile.getExploreStatus().getDailyStartsUsed());
        Assert.assertEquals(5, profile.getExploreStatus().getDailyItemGainLimit());
        Assert.assertEquals(0, profile.getExploreStatus().getDailyItemGainsUsed());
        Assert.assertEquals(0, profile.getExploreStatus().getTreasureMapFragments());
        Assert.assertFalse(profile.getExploreStatus().isHuskyUnlocked());
        Assert.assertTrue(profile.getCollections().isEmpty());
    }

    @Test
    public void petProfileIncludesExploreStatusFromDailyCounters() {
        User user = user(9010L, "explore_status_user");
        setDailyCounter(user.getAccountId(), "explore_start", 2);
        setDailyCounter(user.getAccountId(), "explore_item_gain", 4);

        PetProfileDTO profile = requestProfile(user);

        PetExploreStatusDTO status = profile.getExploreStatus();
        Assert.assertNotNull(status);
        Assert.assertEquals(3, status.getDailyStartLimit());
        Assert.assertEquals(2, status.getDailyStartsUsed());
        Assert.assertEquals(5, status.getDailyItemGainLimit());
        Assert.assertEquals(4, status.getDailyItemGainsUsed());
        Assert.assertEquals(0, status.getTreasureMapFragments());
        Assert.assertFalse(status.isHuskyUnlocked());
        Assert.assertTrue(profile.getCollections().isEmpty());
    }

    @Test
    public void petProfileIncludesInteractionStatusFromDailyCounters() {
        User user = user(9011L, "interaction_status_user");
        setDailyCounter(user.getAccountId(), "interaction_bonus_bones", 120);
        setDailyCounter(user.getAccountId(), "interaction_item_item_battle_direct_hit", 1);
        setDailyCounter(user.getAccountId(), "interaction_item_item_sync_prophecy", 2);

        PetProfileDTO profile = requestProfile(user);

        PetInteractionStatusDTO status = profile.getInteractionStatus();
        Assert.assertNotNull(status);
        Assert.assertEquals(150, status.getDailyBonusCap());
        Assert.assertEquals(120, status.getDailyBonusUsed());
        Assert.assertEquals(30, status.getDailyBonusRemaining());
        Assert.assertEquals(2, status.getSameItemDailyRewardLimit());
        Assert.assertEquals(Integer.valueOf(1), status.getItemRewardCounts().get("item_battle_direct_hit"));
        Assert.assertEquals(Integer.valueOf(1), status.getItemRemainingRewardCounts().get("item_battle_direct_hit"));
        Assert.assertEquals(Integer.valueOf(2), status.getItemRewardCounts().get("item_sync_prophecy"));
        Assert.assertEquals(Integer.valueOf(0), status.getItemRemainingRewardCounts().get("item_sync_prophecy"));
    }

    @Test
    public void petProfileShouldExposeV5TrainingSkillDefinitions() {
        User user = user(9030L, "pet_training_definitions");

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals("v5-explore-training", profile.getTrainingStatus().getDefinitionVersion());
        Assert.assertEquals(Arrays.asList(100, 150, 300, 500, 800),
                profile.getTrainingStatus().getUpgradeCosts());
        Assert.assertFalse(profile.getTrainingStatus().isFreeLearnAvailable());
        Assert.assertEquals(5, profile.getTrainingStatus().getDefinitions().size());
        PetTrainingSkillDefinitionDTO route = findTrainingDefinition(profile, "explore_route");
        Assert.assertEquals("熟路口令", route.getName());
        Assert.assertEquals("🧭", route.getEmoji());
        Assert.assertEquals("探险耗时缩短", route.getDescription());
        Assert.assertEquals(5, route.getLevelEffects().size());
        Assert.assertEquals("耗时 -4%", route.getLevelEffects().get(0));
    }

    @Test
    public void trainingLearnUpgradeAndEquipShouldPersistAccountSkillAndDogSlot() {
        User user = user(9031L, "pet_training_learn");
        PetDogDTO dog = adoptDog(user, "border_collie", "边牧");

        PetProfileDTO learned = parseProfile(handlerProcess(user,
                trainingSkillRequest(PetAction.TRAINING_LEARN, "explore_route", null, 903101L)));
        Assert.assertEquals(200, learned.getAssets().getBones());
        Assert.assertEquals(1, findTrainingSkill(learned, "explore_route").getLevel());

        PetProfileDTO upgraded = parseProfile(handlerProcess(user,
                trainingSkillRequest(PetAction.TRAINING_UPGRADE, "explore_route", null, 903102L)));
        Assert.assertEquals(50, upgraded.getAssets().getBones());
        Assert.assertEquals(2, findTrainingSkill(upgraded, "explore_route").getLevel());

        PetProfileDTO equipped = parseProfile(handlerProcess(user,
                trainingSkillRequest(PetAction.TRAINING_EQUIP, "explore_route", dog.getId(), 903103L)));
        Assert.assertEquals("explore_route", findDog(equipped, dog.getId()).getExploreSkillId());
    }

    @Test
    public void exploreStartShouldSnapshotEquippedTrainingSkillAndShortenDuration() {
        User user = user(9032L, "pet_training_snapshot");
        PetDogDTO dog = adoptDog(user, "border_collie", "边牧");
        handlerProcess(user, trainingSkillRequest(PetAction.TRAINING_LEARN, "explore_route", null, 903201L));
        handlerProcess(user, trainingSkillRequest(PetAction.TRAINING_EQUIP, "explore_route", dog.getId(), 903202L));

        long before = System.currentTimeMillis();
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 903203L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetDogDTO exploringDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("explore_route", exploringDog.getExploreSkillSnapshotId());
        Assert.assertEquals(Integer.valueOf(1), exploringDog.getExploreSkillSnapshotLevel());
        Assert.assertEquals("v5-explore-training", exploringDog.getExploreSkillSnapshotDefinitionVersion());
        long expectedDurationMillis = (long) Math.ceil(TimeUnit.HOURS.toMillis(1) * 0.96D);
        Assert.assertTrue(exploringDog.getExploreEndsAt() >= before + expectedDurationMillis);
        Assert.assertTrue(exploringDog.getExploreEndsAt() <= System.currentTimeMillis() + expectedDurationMillis + 1500L);
    }

    @Test
    public void firstOneHourExploreOpenShouldGrantOneFreeTrainingLearn() {
        User user = user(9033L, "pet_training_free_learn");
        PetDogDTO dog = adoptDog(user, "border_collie", "边牧");
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);

        try {
            new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 903301L));
            Assert.assertTrue(readPetBody(user).isSuccess());
            setExploreEnded(user.getAccountId(), dog.getId());
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 903302L));
            PetExploreOpenResultDTO opened = parseExploreOpenResult(readPetBody(user).getContent());
            Assert.assertTrue(opened.getProfile().getTrainingStatus().isFreeLearnAvailable());

            PetProfileDTO learned = parseProfile(handlerProcess(user,
                    trainingSkillRequest(PetAction.TRAINING_LEARN, "explore_bones", null, 903303L)));

            Assert.assertEquals(opened.getProfile().getAssets().getBones(), learned.getAssets().getBones());
            Assert.assertFalse(learned.getTrainingStatus().isFreeLearnAvailable());
            Assert.assertEquals(1, findTrainingSkill(learned, "explore_bones").getLevel());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void petProfileForNewAccountDoesNotCreateAssetsRow() {
        User user = user(9009L, "profile_only_user");
        Assert.assertFalse(petAssetsExists(user.getAccountId()));

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(9009L, profile.getAccountId());
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertNull(profile.getCompanionDogId());
        Assert.assertFalse(petAssetsExists(user.getAccountId()));
    }

    @Test
    public void petProfileReturnsPositiveInventoryItemsSortedByItemId() {
        User user = user(9010L, "inventory_user");
        insertPetItem(user.getAccountId(), "rare_sticker", 2);
        insertPetItem(user.getAccountId(), "adventure_ticket", 1);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(2, profile.getItems().size());
        assertItem(profile.getItems().get(0), "adventure_ticket", 1);
        assertItem(profile.getItems().get(1), "rare_sticker", 2);
    }

    @Test
    public void petProfileOmitsNonPositiveInventoryItems() {
        User user = user(9011L, "inventory_filter_user");
        insertPetItem(user.getAccountId(), "expired_ticket", 0);
        insertPetItem(user.getAccountId(), "broken_badge", -1);
        insertPetItem(user.getAccountId(), "valid_badge", 3);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "valid_badge", 3);
    }

    @Test
    public void hasPositiveItemOnlyReturnsTrueForPositiveInventory() {
        User user = user(9012L, "positive_item_user");
        insertPetItem(user.getAccountId(), "item_hint", 1);
        insertPetItem(user.getAccountId(), "empty_item", 0);
        insertPetItem(user.getAccountId(), "negative_item", -1);

        Assert.assertTrue(PetProfileService.hasPositiveItem(user.getAccountId(), "item_hint"));
        Assert.assertFalse(PetProfileService.hasPositiveItem(user.getAccountId(), "empty_item"));
        Assert.assertFalse(PetProfileService.hasPositiveItem(user.getAccountId(), "negative_item"));
        Assert.assertFalse(PetProfileService.hasPositiveItem(user.getAccountId(), "missing_item"));
    }

    @Test
    public void addItemIfUnderLimitRejectsNewRowQuantityOverLimit() {
        long accountId = 9013L;
        String itemId = "item_hint";
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true)) {
            cn.xeblog.server.pet.PetItemMapper mapper =
                    session.getMapper(cn.xeblog.server.pet.PetItemMapper.class);

            int changed = mapper.addItemIfUnderLimit(accountId, itemId, 10, 9, System.currentTimeMillis());

            Assert.assertEquals(0, changed);
            Assert.assertNull(mapper.findByAccountIdAndItemId(accountId, itemId));
        }
    }

    @Test
    public void petProfileEchoesRequestId() {
        User user = user();

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.PET_PROFILE);
        request.setRequestId(10001L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.PET_PROFILE, body.getPetAction());
        Assert.assertEquals(Long.valueOf(10001L), body.getRequestId());
    }

    @Test
    public void petProfilePromotesPuppyToAdultWhenStatsAndRaceCountReachThreshold() {
        User user = user(9013L, "adult_stage_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "puppy",
                30, 30, 30, 30, 30, 3, 0);

        PetProfileDTO profile = requestProfile(user);

        PetDogDTO profileDog = findDog(profile, dog.getId());
        Assert.assertEquals("adult", profileDog.getStage());
        Assert.assertEquals(3, profileDog.getRaceCount());
        Assert.assertEquals(0, profileDog.getRaceFirstCount());
    }

    @Test
    public void petProfilePromotesDogToChampionWhenStatsAndRaceFirstReachThreshold() {
        User user = user(9014L, "champion_stage_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "adult",
                60, 60, 60, 60, 60, 3, 1);

        PetProfileDTO profile = requestProfile(user);

        PetDogDTO profileDog = findDog(profile, dog.getId());
        Assert.assertEquals("champion", profileDog.getStage());
        Assert.assertEquals(3, profileDog.getRaceCount());
        Assert.assertEquals(1, profileDog.getRaceFirstCount());
    }

    @Test
    public void petProfileDoesNotDowngradeChampionWhenThresholdsAreNoLongerMet() {
        User user = user(9015L, "champion_no_downgrade_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "champion",
                8, 12, 10, 10, 10, 0, 0);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals("champion", findDog(profile, dog.getId()).getStage());
    }

    @Test
    public void petProfileDoesNotDowngradeAdultWhenThresholdsAreNoLongerMet() {
        User user = user(9016L, "adult_no_downgrade_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "adult",
                8, 12, 10, 10, 10, 0, 0);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals("adult", findDog(profile, dog.getId()).getStage());
    }

    @Test
    public void raceResultRecordsParticipationAndPromotesQualifiedDogToAdult() {
        User user = user(9017L, "race_result_adult_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "puppy",
                30, 30, 30, 30, 30, 2, 0);

        new PetActionHandler().process(user, raceResultRequest(dog.getId(), 2, 98001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.RACE_RESULT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(98001L), body.getRequestId());
        PetDogDTO racedDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("adult", racedDog.getStage());
        Assert.assertEquals(3, racedDog.getRaceCount());
        Assert.assertEquals(0, racedDog.getRaceFirstCount());
        assertDogRaceProgress(user.getAccountId(), dog.getId(), 3, 0);
    }

    @Test
    public void raceResultRecordsFirstPlaceAndPromotesQualifiedDogToChampion() {
        User user = user(9018L, "race_result_champion_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "adult",
                60, 60, 60, 60, 60, 3, 0);

        new PetActionHandler().process(user, raceResultRequest(dog.getId(), 1, 98002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.RACE_RESULT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(98002L), body.getRequestId());
        PetDogDTO racedDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("champion", racedDog.getStage());
        Assert.assertEquals(4, racedDog.getRaceCount());
        Assert.assertEquals(1, racedDog.getRaceFirstCount());
        assertDogRaceProgress(user.getAccountId(), dog.getId(), 4, 1);
    }

    @Test
    public void raceResultRejectsOtherAccountDogWithoutSideEffects() {
        User owner = user(9019L, "race_owner_user");
        PetDogDTO ownerDog = adoptDog(owner, "corgi", "小短腿");
        User other = user(9020L, "race_other_user");

        new PetActionHandler().process(other, raceResultRequest(ownerDog.getId(), 1, 98003L));

        PetResponseDTO body = readPetBody(other);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.RACE_RESULT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(98003L), body.getRequestId());
        Assert.assertEquals("只能结算自己的狗狗赛跑结果", body.getError());
        assertDogRaceProgress(owner.getAccountId(), ownerDog.getId(), 0, 0);
    }

    @Test
    public void raceResultRejectsInvalidRankWithoutSideEffects() {
        User user = user(9021L, "race_invalid_rank_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");

        new PetActionHandler().process(user, raceResultRequest(dog.getId(), 6, 98004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.RACE_RESULT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(98004L), body.getRequestId());
        Assert.assertEquals("狗狗赛跑结果无效", body.getError());
        assertDogRaceProgress(user.getAccountId(), dog.getId(), 0, 0);
    }

    @Test
    public void guestFailureEchoesRequestId() {
        User user = user();
        user.setGuest(true);
        user.setAccountId(0L);

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.PET_PROFILE);
        request.setRequestId(30003L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.PET_PROFILE, body.getPetAction());
        Assert.assertEquals(Long.valueOf(30003L), body.getRequestId());
        Assert.assertEquals("游客不支持狗狗宇宙，请登录账号后再进入", body.getError());
    }

    @Test
    public void emptyPetActionFailureEchoesRequestId() {
        User user = user();

        PetRequestDTO request = new PetRequestDTO();
        request.setRequestId(40004L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertNull(body.getPetAction());
        Assert.assertEquals(Long.valueOf(40004L), body.getRequestId());
        Assert.assertEquals("狗狗操作为空", body.getError());
    }

    @Test
    public void buySlotSucceedsWhenBonesAreEnough() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 1);

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.BUY_SLOT);
        request.setRequestId(70001L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.BUY_SLOT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(70001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(2, profile.getAssets().getDogSlots());
        Assert.assertEquals(300, profile.getAssets().getBones());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(2, persistedProfile.getAssets().getDogSlots());
        Assert.assertEquals(300, persistedProfile.getAssets().getBones());
    }

    @Test
    public void buySlotRejectsWhenBonesAreNotEnoughWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 1999, 1);

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.BUY_SLOT);
        request.setRequestId(70002L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.BUY_SLOT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(70002L), body.getRequestId());
        Assert.assertEquals("骨头币不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertEquals(1999, profile.getAssets().getBones());
    }

    @Test
    public void buySlotRejectsWhenAlreadyAtLimitWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 3000, 2);

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.BUY_SLOT);
        request.setRequestId(70003L);

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.BUY_SLOT, body.getPetAction());
        Assert.assertEquals(Long.valueOf(70003L), body.getRequestId());
        Assert.assertEquals("狗位已达上限", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(2, profile.getAssets().getDogSlots());
        Assert.assertEquals(3000, profile.getAssets().getBones());
    }

    @Test
    public void concurrentBuySlotAllowsOnlyOneSuccessForSameAccount() throws Exception {
        int attempts = 8;
        User seedUser = user();
        setAssets(seedUser.getAccountId(), 5000, 1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<PetResponseDTO> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                User user = user();
                PetRequestDTO request = new PetRequestDTO();
                request.setPetAction(PetAction.BUY_SLOT);
                request.setRequestId(70004L);
                ready.countDown();
                await(start);
                new PetActionHandler().process(user, request);
                responses.add(readPetBody(user));
            });
        }

        Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        int successCount = 0;
        int limitCount = 0;
        for (PetResponseDTO response : responses) {
            if (response.isSuccess()) {
                successCount++;
            } else if ("狗位已达上限".equals(response.getError())) {
                limitCount++;
            }
            Assert.assertEquals(Long.valueOf(70004L), response.getRequestId());
        }
        Assert.assertEquals(attempts, responses.size());
        Assert.assertEquals(1, successCount);
        Assert.assertEquals(attempts - 1, limitCount);

        PetProfileDTO profile = requestProfile(user());
        Assert.assertEquals(2, profile.getAssets().getDogSlots());
        Assert.assertEquals(3000, profile.getAssets().getBones());
    }

    @Test
    public void shopBuyFoodSucceedsForSingleItemAndEchoesRequestId() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 6);

        new PetActionHandler().process(user, shopBuyRequest("food", 1, 90001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(270, profile.getAssets().getBones());
        Assert.assertEquals(7, profile.getAssets().getFood());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(270, persistedProfile.getAssets().getBones());
        Assert.assertEquals(7, persistedProfile.getAssets().getFood());
    }

    @Test
    public void shopBuyFoodSucceedsForBatchQuantity() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 6);

        new PetActionHandler().process(user, shopBuyRequest("food", 3, 90002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90002L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(210, profile.getAssets().getBones());
        Assert.assertEquals(9, profile.getAssets().getFood());
    }

    @Test
    public void shopBuyFoodRejectsWhenBonesAreNotEnoughWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 29, 1);
        setFood(user.getAccountId(), 6);

        new PetActionHandler().process(user, shopBuyRequest("food", 1, 90003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90003L), body.getRequestId());
        Assert.assertEquals("骨头币不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(29, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
    }

    @Test
    public void shopBuyFoodRejectsWhenQuantityWouldExceedFoodLimitWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 98);

        new PetActionHandler().process(user, shopBuyRequest("food", 2, 90004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90004L), body.getRequestId());
        Assert.assertEquals("狗粮持有数量不能超过 99", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(98, profile.getAssets().getFood());
    }

    @Test
    public void shopBuyFoodRejectsWhenFoodAlreadyAtLimitWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 99);

        new PetActionHandler().process(user, shopBuyRequest("food", 1, 90005L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90005L), body.getRequestId());
        Assert.assertEquals("狗粮持有数量不能超过 99", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(99, profile.getAssets().getFood());
    }

    @Test
    public void shopBuyMakeupCardSucceedsForSingleItemAndEchoesRequestId() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setMakeupCards(user.getAccountId(), 0);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 1, 91001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(150, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(1, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(150, persistedProfile.getAssets().getBones());
        Assert.assertEquals(1, persistedProfile.getAssets().getMakeupCards());
    }

    @Test
    public void shopBuyMakeupCardSucceedsForBatchQuantity() {
        User user = user();
        setAssets(user.getAccountId(), 450, 1);
        setMakeupCards(user.getAccountId(), 0);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 2, 91002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91002L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(150, profile.getAssets().getBones());
        Assert.assertEquals(2, profile.getAssets().getMakeupCards());
        Assert.assertEquals(2, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyMakeupCardRejectsWhenBonesAreNotEnoughWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 149, 1);
        setMakeupCards(user.getAccountId(), 0);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 1, 91003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91003L), body.getRequestId());
        Assert.assertEquals("骨头币不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(149, profile.getAssets().getBones());
        Assert.assertEquals(0, profile.getAssets().getMakeupCards());
        Assert.assertEquals(0, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyMakeupCardRejectsWhenQuantityWouldExceedHoldLimitWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setMakeupCards(user.getAccountId(), 2);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 2, 91004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91004L), body.getRequestId());
        Assert.assertEquals("补签卡持有数量不能超过 3", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(2, profile.getAssets().getMakeupCards());
        Assert.assertEquals(0, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyMakeupCardRejectsWhenMonthlyLimitWouldBeExceededWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setMakeupCards(user.getAccountId(), 0);
        setMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy", 1);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 2, 91005L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91005L), body.getRequestId());
        Assert.assertEquals("本月补签卡购买次数已达上限", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(0, profile.getAssets().getMakeupCards());
        Assert.assertEquals(1, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyMakeupCardIgnoresPreviousMonthCounter() {
        User user = user();
        YearMonth currentMonth = YearMonth.now();
        setAssets(user.getAccountId(), 300, 1);
        setMakeupCards(user.getAccountId(), 0);
        insertDailyCounter(user.getAccountId(), currentMonth.minusMonths(1).toString(),
                "shop_makeup_card_buy", 2);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 1, 91006L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91006L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(150, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(1, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
        Assert.assertEquals(2, countCounter(user.getAccountId(), currentMonth.minusMonths(1).toString(),
                "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyMakeupCardRejectsInvalidQuantityWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setMakeupCards(user.getAccountId(), 0);

        new PetActionHandler().process(user, shopBuyRequest("makeup_card", 0, 91007L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(91007L), body.getRequestId());
        Assert.assertEquals("购买数量必须为正整数", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(0, profile.getAssets().getMakeupCards());
        Assert.assertEquals(0, countMonthlyCounter(user.getAccountId(), "shop_makeup_card_buy"));
    }

    @Test
    public void shopBuyNormalItemSucceedsForSingleItemAndEchoesRequestId() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 1, 92001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(220, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(220, persistedProfile.getAssets().getBones());
        Assert.assertEquals(1, persistedProfile.getItems().size());
        assertItem(persistedProfile.getItems().get(0), "item_hint", 1);
    }

    @Test
    public void interactionItemRewardUsesHalfConfiguredRewardWhenRequestIsHigher() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        PetProfileDTO profile = PetProfileService.applyInteractionItemReward(
                user.getAccountId(), "item_battle_direct_hit", 80);

        Assert.assertEquals(340, profile.getAssets().getBones());
        Assert.assertEquals(40, countDailyCounter(user.getAccountId(), "interaction_bonus_bones"));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "interaction_item_item_battle_direct_hit"));
    }

    @Test
    public void interactionItemRewardAllowsSameItemAtMostTwicePerDay() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        PetProfileService.applyInteractionItemReward(user.getAccountId(), "item_sync_prophecy", 20);
        PetProfileService.applyInteractionItemReward(user.getAccountId(), "item_sync_prophecy", 20);
        PetProfileDTO profile = PetProfileService.applyInteractionItemReward(
                user.getAccountId(), "item_sync_prophecy", 20);

        Assert.assertEquals(340, profile.getAssets().getBones());
        Assert.assertEquals(40, countDailyCounter(user.getAccountId(), "interaction_bonus_bones"));
        Assert.assertEquals(2, countDailyCounter(user.getAccountId(), "interaction_item_item_sync_prophecy"));
    }

    @Test
    public void interactionItemRewardRespectsDailyAccountCap() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setDailyCounter(user.getAccountId(), "interaction_bonus_bones", 145);

        PetProfileDTO cappedProfile = PetProfileService.applyInteractionItemReward(
                user.getAccountId(), "item_gomoku_prediction", 50);
        PetProfileDTO fullProfile = PetProfileService.applyInteractionItemReward(
                user.getAccountId(), "item_quiz_duel", 30);

        Assert.assertEquals(305, cappedProfile.getAssets().getBones());
        Assert.assertEquals(305, fullProfile.getAssets().getBones());
        Assert.assertEquals(150, countDailyCounter(user.getAccountId(), "interaction_bonus_bones"));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "interaction_item_item_gomoku_prediction"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "interaction_item_item_quiz_duel"));
    }

    @Test
    public void interactionItemRewardRejectsNonInteractionItem() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        try {
            PetProfileService.applyInteractionItemReward(user.getAccountId(), "item_hint", 20);
            Assert.fail("普通玩法道具不应发放互动奖励");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("该道具没有互动奖励", e.getMessage());
        }

        Assert.assertEquals(300, requestProfile(user).getAssets().getBones());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "interaction_bonus_bones"));
    }

    @Test
    public void shopBuyNormalItemSucceedsForBatchQuantityAndReturnsSortedItems() {
        User user = user();
        setAssets(user.getAccountId(), 500, 1);
        insertPetItem(user.getAccountId(), "item_battle_echo", 1);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 2, 92002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92002L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(340, profile.getAssets().getBones());
        Assert.assertEquals(2, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_battle_echo", 1);
        assertItem(profile.getItems().get(1), "item_hint", 2);
        Assert.assertEquals(2, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyNormalItemRejectsWhenBonesAreNotEnoughWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 79, 1);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 1, 92003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92003L), body.getRequestId());
        Assert.assertEquals("骨头币不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(79, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyNormalItemRejectsWhenItemLimitWouldBeExceededWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_hint", 8);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 2, 92004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92004L), body.getRequestId());
        Assert.assertEquals("道具卡持有数量不能超过 9", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 8);
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyNormalItemRejectsWhenDailySharedLimitWouldBeExceededWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 500, 1);
        insertPetItem(user.getAccountId(), "item_hint", 1);
        setDailyCounter(user.getAccountId(), "shop_normal_item_buy", 2);

        new PetActionHandler().process(user, shopBuyRequest("item_battle_direct_hit", 2, 92005L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92005L), body.getRequestId());
        Assert.assertEquals("今日普通道具购买次数已达上限", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(500, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);
        Assert.assertEquals(2, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyNormalItemIgnoresYesterdayCounter() {
        User user = user();
        LocalDate today = LocalDate.now();
        setAssets(user.getAccountId(), 300, 1);
        insertDailyCounter(user.getAccountId(), today.minusDays(1).toString(),
                "shop_normal_item_buy", 3);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 1, 92006L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92006L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(220, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
        Assert.assertEquals(3, countCounter(user.getAccountId(), today.minusDays(1).toString(),
                "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyRejectsRareEpicAndUnknownItemWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 500, 1);
        String todayRareItemId = dailyRareShopItemId(LocalDate.now());
        String unavailableRareItemId = "item_battle_pebble".equals(todayRareItemId) ? "item_turtle_probe" : "item_battle_pebble";

        new PetActionHandler().process(user, shopBuyRequest(unavailableRareItemId, 1, 92007L));
        PetResponseDTO rareBody = readPetBody(user);
        Assert.assertFalse(rareBody.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, rareBody.getPetAction());
        Assert.assertEquals(Long.valueOf(92007L), rareBody.getRequestId());
        Assert.assertEquals("今日未出售该稀有道具", rareBody.getError());

        new PetActionHandler().process(user, shopBuyRequest("item_lucky_day", 1, 92008L));
        PetResponseDTO epicBody = readPetBody(user);
        Assert.assertFalse(epicBody.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, epicBody.getPetAction());
        Assert.assertEquals(Long.valueOf(92008L), epicBody.getRequestId());
        Assert.assertEquals("暂不支持该商店商品", epicBody.getError());

        new PetActionHandler().process(user, shopBuyRequest("unknown_item", 1, 92009L));
        PetResponseDTO unknownBody = readPetBody(user);
        Assert.assertFalse(unknownBody.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, unknownBody.getPetAction());
        Assert.assertEquals(Long.valueOf(92009L), unknownBody.getRequestId());
        Assert.assertEquals("暂不支持该商店商品", unknownBody.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(500, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_daily_rare_item_buy"));
    }

    @Test
    public void shopBuyLuckyBagSucceedsForSingleBagAndReturnsOneRandomItem() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 1, 92101L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92101L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(50, profile.getAssets().getBones());
        Assert.assertEquals(1, totalInventoryCount(profile));
        Assert.assertEquals(1, profile.getItems().size());
        Assert.assertTrue(luckyBagItemIds().contains(profile.getItems().get(0).getItemId()));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void shopBuyLuckyBagSucceedsForQuantityTwoWithoutUsingNormalItemCounter() {
        User user = user();
        setAssets(user.getAccountId(), 600, 1);

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 2, 92102L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92102L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(100, profile.getAssets().getBones());
        Assert.assertEquals(2, totalInventoryCount(profile));
        for (PetInventoryItemDTO item : profile.getItems()) {
            Assert.assertTrue(luckyBagItemIds().contains(item.getItemId()));
        }
        Assert.assertEquals(2, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyLuckyBagDailyLimitRejectsWithoutPartialSuccess() {
        User user = user();
        setAssets(user.getAccountId(), 600, 1);
        setDailyCounter(user.getAccountId(), "shop_lucky_bag_buy", 1);

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 2, 92103L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92103L), body.getRequestId());
        Assert.assertEquals("今日狗狗福袋购买次数已达上限", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(600, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void concurrentShopBuyLuckyBagAllowsOnlyDailyLimitForSameAccount() throws Exception {
        int attempts = 6;
        User seedUser = user();
        setAssets(seedUser.getAccountId(), 2000, 1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<PetResponseDTO> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                User user = user();
                ready.countDown();
                await(start);
                new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 1, 92106L));
                responses.add(readPetBody(user));
            });
        }

        Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        int successCount = 0;
        int limitCount = 0;
        for (PetResponseDTO response : responses) {
            if (response.isSuccess()) {
                successCount++;
            } else if ("今日狗狗福袋购买次数已达上限".equals(response.getError())) {
                limitCount++;
            }
            Assert.assertEquals(Long.valueOf(92106L), response.getRequestId());
        }
        Assert.assertEquals(attempts, responses.size());
        Assert.assertEquals(2, successCount);
        Assert.assertEquals(attempts - 2, limitCount);

        PetProfileDTO profile = requestProfile(seedUser);
        Assert.assertEquals(1500, profile.getAssets().getBones());
        Assert.assertEquals(2, totalInventoryCount(profile));
        Assert.assertEquals(2, countDailyCounter(seedUser.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void shopBuyLuckyBagRejectsWhenBonesAreNotEnoughWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 249, 1);

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 1, 92104L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92104L), body.getRequestId());
        Assert.assertEquals("骨头币不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(249, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void shopBuyLuckyBagRejectsWhenAllRewardItemsAreFullWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 500, 1);
        for (String itemId : luckyBagItemIds()) {
            insertPetItem(user.getAccountId(), itemId, 9);
        }

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 1, 92105L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92105L), body.getRequestId());
        Assert.assertEquals("道具背包已满", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(500, profile.getAssets().getBones());
        Assert.assertEquals(luckyBagItemIds().size() * 9, totalInventoryCount(profile));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void shopBuyLuckyBagCanAwardEachRarityWhenOtherRaritiesAreFull() {
        assertLuckyBagAwardsOnlyAvailableRarity(92111L, normalLuckyBagItemIds(),
                rareLuckyBagItemIds(), epicLuckyBagItemIds());
        assertLuckyBagAwardsOnlyAvailableRarity(92112L, rareLuckyBagItemIds(),
                normalLuckyBagItemIds(), epicLuckyBagItemIds());
        assertLuckyBagAwardsOnlyAvailableRarity(92113L, epicLuckyBagItemIds(),
                normalLuckyBagItemIds(), rareLuckyBagItemIds());
    }

    @Test
    public void shopBuyNormalItemRejectsInvalidQuantityWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);

        new PetActionHandler().process(user, shopBuyRequest("item_hint", 0, 92010L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(92010L), body.getRequestId());
        Assert.assertEquals("购买数量必须为正整数", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
    }

    @Test
    public void shopBuyDailyRareItemSucceedsForTodayOffer() {
        User user = user();
        setAssets(user.getAccountId(), 1000, 1);
        String itemId = dailyRareShopItemId(LocalDate.now());

        new PetActionHandler().process(user, shopBuyRequest(itemId, 1, 90008L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90008L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(680, profile.getAssets().getBones());
        Assert.assertEquals(1, countItem(user.getAccountId(), itemId));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_daily_rare_item_buy"));
    }

    @Test
    public void shopBuyDailyRareItemRejectsNonTodayOfferWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 1000, 1);
        String todayItemId = dailyRareShopItemId(LocalDate.now());
        String otherItemId = "item_mine_guard".equals(todayItemId) ? "item_metal_detector" : "item_mine_guard";

        new PetActionHandler().process(user, shopBuyRequest(otherItemId, 1, 90009L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90009L), body.getRequestId());
        Assert.assertEquals("今日未出售该稀有道具", body.getError());
        Assert.assertEquals(0, countItem(user.getAccountId(), otherItemId));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_daily_rare_item_buy"));
        Assert.assertEquals(1000, requestProfile(user).getAssets().getBones());
    }

    @Test
    public void shopBuyRejectsUnsupportedItemWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 6);

        new PetActionHandler().process(user, shopBuyRequest("unknown_item", 1, 90006L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90006L), body.getRequestId());
        Assert.assertEquals("暂不支持该商店商品", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
    }

    @Test
    public void shopBuyRejectsInvalidQuantityWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 6);

        new PetActionHandler().process(user, shopBuyRequest("food", 0, 90007L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SHOP_BUY, body.getPetAction());
        Assert.assertEquals(Long.valueOf(90007L), body.getRequestId());
        Assert.assertEquals("购买数量必须为正整数", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
    }

    @Test
    public void sellNormalItemSucceedsForSingleItemAndEchoesRequestId() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_hint", 2);

        new PetActionHandler().process(user, sellItemRequest("item_hint", 1, 93001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(320, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(320, persistedProfile.getAssets().getBones());
        Assert.assertEquals(1, persistedProfile.getItems().size());
        assertItem(persistedProfile.getItems().get(0), "item_hint", 1);
    }

    @Test
    public void sellRareItemSucceedsForSingleItem() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_battle_pebble", 1);

        new PetActionHandler().process(user, sellItemRequest("item_battle_pebble", 1, 93002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93002L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(380, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
    }

    @Test
    public void sellEpicItemSucceedsForSingleItem() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_lucky_day", 1);

        new PetActionHandler().process(user, sellItemRequest("item_lucky_day", 1, 93003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93003L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(500, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
    }

    @Test
    public void sellNormalItemSucceedsForBatchQuantity() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_battle_echo", 3);

        new PetActionHandler().process(user, sellItemRequest("item_battle_echo", 2, 93004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93004L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(340, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_battle_echo", 1);
    }

    @Test
    public void sellItemRejectsInsufficientInventoryWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_hint", 1);

        new PetActionHandler().process(user, sellItemRequest("item_hint", 2, 93005L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93005L), body.getRequestId());
        Assert.assertEquals("道具数量不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);
    }

    @Test
    public void sellItemRejectsUnsupportedItemsWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setFood(user.getAccountId(), 6);
        setMakeupCards(user.getAccountId(), 1);
        insertPetItem(user.getAccountId(), "lucky_bag", 1);

        new PetActionHandler().process(user, sellItemRequest("lucky_bag", 1, 93006L));
        PetResponseDTO unknownBody = readPetBody(user);
        Assert.assertFalse(unknownBody.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, unknownBody.getPetAction());
        Assert.assertEquals(Long.valueOf(93006L), unknownBody.getRequestId());
        Assert.assertEquals("暂不支持出售该物品", unknownBody.getError());

        new PetActionHandler().process(user, sellItemRequest("food", 1, 93007L));
        PetResponseDTO foodBody = readPetBody(user);
        Assert.assertFalse(foodBody.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, foodBody.getPetAction());
        Assert.assertEquals(Long.valueOf(93007L), foodBody.getRequestId());
        Assert.assertEquals("暂不支持出售该物品", foodBody.getError());

        new PetActionHandler().process(user, sellItemRequest("makeup_card", 1, 93008L));
        PetResponseDTO makeupBody = readPetBody(user);
        Assert.assertFalse(makeupBody.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, makeupBody.getPetAction());
        Assert.assertEquals(Long.valueOf(93008L), makeupBody.getRequestId());
        Assert.assertEquals("暂不支持出售该物品", makeupBody.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(6, profile.getAssets().getFood());
        Assert.assertEquals(1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "lucky_bag", 1);
    }

    @Test
    public void sellItemRejectsInvalidQuantityWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_hint", 1);

        new PetActionHandler().process(user, sellItemRequest("item_hint", 0, 93009L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93009L), body.getRequestId());
        Assert.assertEquals("出售数量必须为正整数", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_hint", 1);
    }

    @Test
    public void sellItemRejectsOverflowRewardWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        int quantity = Integer.MAX_VALUE / 200 + 1;
        insertPetItem(user.getAccountId(), "item_lucky_day", Integer.MAX_VALUE);

        new PetActionHandler().process(user, sellItemRequest("item_lucky_day", quantity, 93012L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93012L), body.getRequestId());
        Assert.assertEquals("出售数量过大", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getItems().size());
        assertItem(profile.getItems().get(0), "item_lucky_day", Integer.MAX_VALUE);
    }

    @Test
    public void malformedSellItemContentReturnsPetFailureResponse() {
        User user = user();

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SELL_ITEM);
        request.setRequestId(93010L);
        request.setContent("bad-content");

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93010L), body.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", body.getError());
    }

    @Test
    public void sellItemCreatesAssetsWhenOnlyInventoryExists() {
        User user = user(93011L, "sell_item_without_assets");
        insertPetItem(user.getAccountId(), "item_hint", 1);
        Assert.assertFalse(petAssetsExists(user.getAccountId()));

        new PetActionHandler().process(user, sellItemRequest("item_hint", 1, 93011L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93011L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(320, profile.getAssets().getBones());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertTrue(petAssetsExists(user.getAccountId()));
    }

    @Test
    public void sellCollectionKeepsDiscoveredWhenInventoryBecomesZero() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetCollection(user.getAccountId(), "back_hill_ball", 2, true);

        new PetActionHandler().process(user, sellCollectionRequest("back_hill_ball", 2, 93013L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_COLLECTION, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93013L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(330, profile.getAssets().getBones());
        PetCollectionItemDTO collection = findCollection(profile, "back_hill_ball");
        Assert.assertNotNull(collection);
        Assert.assertEquals(0, collection.getCount());
        Assert.assertTrue(collection.isDiscovered());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(330, persistedProfile.getAssets().getBones());
        PetCollectionItemDTO persistedCollection = findCollection(persistedProfile, "back_hill_ball");
        Assert.assertNotNull(persistedCollection);
        Assert.assertEquals(0, persistedCollection.getCount());
        Assert.assertTrue(persistedCollection.isDiscovered());
    }

    @Test
    public void sellCollectionRejectsInsufficientInventoryWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetCollection(user.getAccountId(), "back_hill_branch", 1, true);

        new PetActionHandler().process(user, sellCollectionRequest("back_hill_branch", 2, 93014L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_COLLECTION, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93014L), body.getRequestId());
        Assert.assertEquals("收藏品数量不足", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        PetCollectionItemDTO collection = findCollection(profile, "back_hill_branch");
        Assert.assertNotNull(collection);
        Assert.assertEquals(1, collection.getCount());
        Assert.assertTrue(collection.isDiscovered());
    }

    @Test
    public void sellCollectionRejectsTreasureMapFragmentsWithoutSideEffects() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetCollection(user.getAccountId(), "treasure_map_fragment", 1, true);

        new PetActionHandler().process(user, sellCollectionRequest("treasure_map_fragment", 1, 93015L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SELL_COLLECTION, body.getPetAction());
        Assert.assertEquals(Long.valueOf(93015L), body.getRequestId());
        Assert.assertEquals("暂不支持出售该收藏品", body.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(300, profile.getAssets().getBones());
        PetCollectionItemDTO collection = findCollection(profile, "treasure_map_fragment");
        Assert.assertNotNull(collection);
        Assert.assertEquals(1, collection.getCount());
        Assert.assertTrue(collection.isDiscovered());
    }

    @Test
    public void useItemLuckyDayConsumesItemAndDoublesMiniGameBoneRewardAndLimits() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        insertPetItem(user.getAccountId(), "item_lucky_day", 1);
        setDailyCounter(user.getAccountId(), "mini_game_complete", 5);
        setDailyCounter(user.getAccountId(), "mini_game_win", 3);

        new PetActionHandler().process(user, useItemRequest("item_lucky_day", null, 94039L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94039L), body.getRequestId());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_lucky_day"));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_lucky_day"));

        PetProfileDTO rewarded = PetProfileService.applyMiniGameResult(
                user.getAccountId(), Game.QUICK_QUIZ, true, 60);

        Assert.assertEquals(360, rewarded.getAssets().getBones());
        Assert.assertEquals(6, countDailyCounter(user.getAccountId(), "mini_game_complete"));
        Assert.assertEquals(4, countDailyCounter(user.getAccountId(), "mini_game_win"));
    }

    @Test
    public void luckyDayDoesNotExceedDoubledMiniGameBoneRewardLimits() {
        User user = user();
        setAssets(user.getAccountId(), 300, 1);
        setDailyCounter(user.getAccountId(), "use_item_lucky_day", 1);
        setDailyCounter(user.getAccountId(), "mini_game_complete", 10);
        setDailyCounter(user.getAccountId(), "mini_game_win", 6);

        PetProfileDTO rewarded = PetProfileService.applyMiniGameResult(
                user.getAccountId(), Game.QUICK_QUIZ, true, 60);

        Assert.assertEquals(300, rewarded.getAssets().getBones());
        Assert.assertEquals(10, countDailyCounter(user.getAccountId(), "mini_game_complete"));
        Assert.assertEquals(6, countDailyCounter(user.getAccountId(), "mini_game_win"));
    }

    @Test
    public void useItemFeastConsumesOneItemAndRefillsDogEnergyToCurrentLimit() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setEnergyLimit(user.getAccountId(), 12);
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 94001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(12, findDog(profile, dog.getId()).getEnergy());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "feed_food"));

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(12, findDog(persistedProfile, dog.getId()).getEnergy());
        Assert.assertTrue(persistedProfile.getItems().isEmpty());
    }

    @Test
    public void useItemFeastAcceptsExplicitQuantityOneAndDoesNotUseFeedLimit() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 2, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);
        setDailyCounter(user.getAccountId(), "feed_food", 5);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 1, 94011L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94011L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(10, findDog(profile, dog.getId()).getEnergy());
        Assert.assertTrue(profile.getItems().isEmpty());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_feast"));
        Assert.assertEquals(5, countDailyCounter(user.getAccountId(), "feed_food"));
    }

    @Test
    public void useItemFeastRejectsFullEnergyDogWithoutConsumingItemOrCounter() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 94002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94002L), body.getRequestId());
        Assert.assertEquals("狗狗活力已满", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
        Assert.assertEquals(10, findDog(requestProfile(user), dog.getId()).getEnergy());
    }

    @Test
    public void useItemFeastRefreshesExpiredEnergyBeforeRejectingFullEnergyDog() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 2, LocalDate.now().minusDays(1).toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 94012L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94012L), body.getRequestId());
        Assert.assertEquals("狗狗活力已满", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
        Assert.assertEquals(10, findDog(requestProfile(user), dog.getId()).getEnergy());
    }

    @Test
    public void useItemFeastRejectsInsufficientInventoryWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 94003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94003L), body.getRequestId());
        Assert.assertEquals("道具数量不足", body.getError());
        Assert.assertEquals(3, findDog(requestProfile(user), dog.getId()).getEnergy());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
    }

    @Test
    public void useItemFeastRejectsDailyLimitWithoutConsumingItem() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);
        setDailyCounter(user.getAccountId(), "use_item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 94004L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94004L), body.getRequestId());
        Assert.assertEquals("今日美食大餐已使用", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_feast"));
        Assert.assertEquals(3, findDog(requestProfile(user), dog.getId()).getEnergy());
    }

    @Test
    public void useItemFeastRejectsMissingOrOtherAccountDogWithoutSideEffects() {
        User user = user();
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", "missing-dog", 94005L));

        PetResponseDTO missingBody = readPetBody(user);
        Assert.assertFalse(missingBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, missingBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94005L), missingBody.getRequestId());
        Assert.assertEquals("只能给自己的狗狗使用道具", missingBody.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));

        User other = user(2002L, "other_user");
        PetDogDTO otherDog = adoptDog(other, "poodle", "贵宾");
        new PetActionHandler().process(user, useItemRequest("item_feast", otherDog.getId(), 94006L));

        PetResponseDTO otherBody = readPetBody(user);
        Assert.assertFalse(otherBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, otherBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94006L), otherBody.getRequestId());
        Assert.assertEquals("只能给自己的狗狗使用道具", otherBody.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
    }

    @Test
    public void useItemRejectsUnsupportedItemWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_hint", 1);

        new PetActionHandler().process(user, useItemRequest("item_hint", dog.getId(), 94007L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94007L), body.getRequestId());
        Assert.assertEquals("暂不支持该道具", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_hint"));
        Assert.assertEquals(3, findDog(requestProfile(user), dog.getId()).getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
    }

    @Test
    public void useItemRejectsInvalidOrBlankParametersWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), 0, 94008L));
        PetResponseDTO invalidQuantityBody = readPetBody(user);
        Assert.assertFalse(invalidQuantityBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, invalidQuantityBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94008L), invalidQuantityBody.getRequestId());
        Assert.assertEquals("道具使用数量必须为 1", invalidQuantityBody.getError());

        new PetActionHandler().process(user, useItemRequest("", dog.getId(), 94009L));
        PetResponseDTO blankItemBody = readPetBody(user);
        Assert.assertFalse(blankItemBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, blankItemBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94009L), blankItemBody.getRequestId());
        Assert.assertEquals("道具不能为空", blankItemBody.getError());

        new PetActionHandler().process(user, useItemRequest("item_feast", "", 94010L));
        PetResponseDTO blankDogBody = readPetBody(user);
        Assert.assertFalse(blankDogBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, blankDogBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94010L), blankDogBody.getRequestId());
        Assert.assertEquals("狗狗不能为空", blankDogBody.getError());

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(3, findDog(requestProfile(user), dog.getId()).getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
    }

    @Test
    public void useItemRejectsMissingOrNullQuantityWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 3, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), "item_feast", 1);

        new PetActionHandler().process(user, useItemRequestWithoutQuantity("item_feast", dog.getId(), 94028L));
        PetResponseDTO missingQuantityBody = readPetBody(user);
        Assert.assertFalse(missingQuantityBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, missingQuantityBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94028L), missingQuantityBody.getRequestId());
        Assert.assertEquals("道具使用数量必须为 1", missingQuantityBody.getError());

        new PetActionHandler().process(user, useItemRequest("item_feast", dog.getId(), null, 94029L));
        PetResponseDTO nullQuantityBody = readPetBody(user);
        Assert.assertFalse(nullQuantityBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, nullQuantityBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94029L), nullQuantityBody.getRequestId());
        Assert.assertEquals("道具使用数量必须为 1", nullQuantityBody.getError());

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_feast"));
        Assert.assertEquals(3, findDog(requestProfile(user), dog.getId()).getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_feast"));
    }

    @Test
    public void useItemExpressFinishesCurrentExploreIntoBackHillChest() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 94013L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94014L));

        PetResponseDTO useBody = readPetBody(user);
        Assert.assertTrue(useBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, useBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94014L), useBody.getRequestId());
        PetProfileDTO useProfile = (PetProfileDTO) useBody.getContent();
        PetDogDTO expressDog = findDog(useProfile, dog.getId());
        Assert.assertEquals("idle", expressDog.getStatus());
        Assert.assertNull(expressDog.getExploreLocation());
        Assert.assertNull(expressDog.getExploreEndsAt());
        Assert.assertEquals(0, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_express"));

        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, useItemRequest(BACK_HILL_CHEST_ITEM_ID, null, 94015L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            Assert.assertEquals(PetAction.USE_ITEM, openBody.getPetAction());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO openProfile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(2, rewards.size());
            Assert.assertEquals(325, openProfile.getAssets().getBones());
            Assert.assertEquals(0, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
            PetDogDTO openedDog = findDog(openProfile, dog.getId());
            Assert.assertEquals("idle", openedDog.getStatus());
            Assert.assertNull(openedDog.getExploreLocation());
            Assert.assertNull(openedDog.getExploreEndsAt());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void useItemExpressSettlesLegacyExploreWithoutDurationFieldIntoChest() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 94032L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        clearExploreDurationHours(user.getAccountId(), dog.getId());
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94033L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));

        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, useItemRequest(BACK_HILL_CHEST_ITEM_ID, null, 94034L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO openProfile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(2, rewards.size());
            Assert.assertEquals(325, openProfile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void useItemExpressSettlesLegacyExploreAfterExpiredEnergyRefresh() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 94036L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setLegacyExploreWindow(user.getAccountId(), dog.getId(),
                System.currentTimeMillis() - 2L * 60L * 60L * 1000L,
                System.currentTimeMillis() + 2L * 60L * 60L * 1000L);
        setDogEnergy(user.getAccountId(), dog.getId(), 2, LocalDate.now().minusDays(1).toString());
        PetDogDTO refreshedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("exploring", refreshedDog.getStatus());
        Assert.assertEquals(10, refreshedDog.getEnergy());
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94037L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));

        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, useItemRequest(BACK_HILL_CHEST_ITEM_ID, null, 94038L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO openProfile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(2, rewards.size());
            Assert.assertEquals(325, openProfile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void useItemExpressSettlesLegacyExploreAfterRename() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 94039L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setLegacyExploreWindow(user.getAccountId(), dog.getId(),
                System.currentTimeMillis() - 2L * 60L * 60L * 1000L,
                System.currentTimeMillis() + 2L * 60L * 60L * 1000L);

        PetRequestDTO rename = new PetRequestDTO();
        rename.setPetAction(PetAction.RENAME);
        rename.setContent(new PetRenameDTO(dog.getId(), "探险短腿"));
        new PetActionHandler().process(user, rename);
        Assert.assertTrue(readPetBody(user).isSuccess());

        assertBackHillChestSettledAfterExpress(user, dog.getId(), 94040L, 94041L);
    }

    @Test
    public void useItemExpressSettlesLegacyExploreAfterFeed() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 94042L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setLegacyExploreWindow(user.getAccountId(), dog.getId(),
                System.currentTimeMillis() - 2L * 60L * 60L * 1000L,
                System.currentTimeMillis() + 2L * 60L * 60L * 1000L);
        setDogEnergy(user.getAccountId(), dog.getId(), 2, LocalDate.now().toString());

        new PetActionHandler().process(user, feedRequest(dog.getId()));
        Assert.assertTrue(readPetBody(user).isSuccess());

        assertBackHillChestSettledAfterExpress(user, dog.getId(), 94043L, 94044L);
    }

    @Test
    public void useItemExpressRejectsFinishedExploreWithoutConsumingItemOrCounter() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 94026L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        insertPetItem(user.getAccountId(), "item_express", 1);
        setExploreEnded(user.getAccountId(), dog.getId());
        PetDogDTO settledDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", settledDog.getStatus());
        Assert.assertNull(settledDog.getExploreEndsAt());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94027L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94027L), body.getRequestId());
        Assert.assertEquals("只有探险中的狗狗可以使用加急快递", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
    }

    @Test
    public void useItemExpressResetsInvalidExploreWithoutConsumingItemOrCounter() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 94030L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        insertPetItem(user.getAccountId(), "item_express", 1);
        clearExploreEndsAt(user.getAccountId(), dog.getId());

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94031L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94031L), body.getRequestId());
        Assert.assertEquals("探险数据异常，已重置，请重新开始探险", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
    }

    @Test
    public void useItemExpressRejectsNonExploringDogWithoutConsumingItemOrCounter() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94016L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94016L), body.getRequestId());
        Assert.assertEquals("只有探险中的狗狗可以使用加急快递", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
        Assert.assertEquals("idle", findDog(requestProfile(user), dog.getId()).getStatus());
    }

    @Test
    public void useItemExpressRejectsInsufficientInventoryWithoutCounterOrExploreChange() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 94017L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        Long beforeEndsAt = findDog(requestProfile(user), dog.getId()).getExploreEndsAt();

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94018L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94018L), body.getRequestId());
        Assert.assertEquals("道具数量不足", body.getError());
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("exploring", persistedDog.getStatus());
        Assert.assertEquals(beforeEndsAt, persistedDog.getExploreEndsAt());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
    }

    @Test
    public void useItemExpressRejectsDailyLimitWithoutConsumingItemOrExploreChange() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 94019L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        insertPetItem(user.getAccountId(), "item_express", 1);
        setDailyCounter(user.getAccountId(), "use_item_express", 1);
        Long beforeEndsAt = findDog(requestProfile(user), dog.getId()).getExploreEndsAt();

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 94020L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94020L), body.getRequestId());
        Assert.assertEquals("今日加急快递已使用", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "use_item_express"));
        Assert.assertEquals(beforeEndsAt, findDog(requestProfile(user), dog.getId()).getExploreEndsAt());
    }

    @Test
    public void useItemExpressRejectsInvalidQuantityWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 94021L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dog.getId(), 2, 94022L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
        Assert.assertEquals(Long.valueOf(94022L), body.getRequestId());
        Assert.assertEquals("道具使用数量必须为 1", body.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
    }

    @Test
    public void useItemExpressRejectsMissingOrOtherAccountDogWithoutSideEffects() {
        User user = user();
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", "missing-dog", 94023L));

        PetResponseDTO missingBody = readPetBody(user);
        Assert.assertFalse(missingBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, missingBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94023L), missingBody.getRequestId());
        Assert.assertEquals("只能给自己的狗狗使用加急快递", missingBody.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));

        User other = user(2003L, "express_other_user");
        PetDogDTO otherDog = adoptDog(other, "poodle", "贵宾");
        new PetActionHandler().process(other, exploreStartRequest(otherDog.getId(), "back_hill", 1, 94024L));
        Assert.assertTrue(readPetBody(other).isSuccess());

        new PetActionHandler().process(user, useItemRequest("item_express", otherDog.getId(), 94025L));

        PetResponseDTO otherBody = readPetBody(user);
        Assert.assertFalse(otherBody.isSuccess());
        Assert.assertEquals(PetAction.USE_ITEM, otherBody.getPetAction());
        Assert.assertEquals(Long.valueOf(94025L), otherBody.getRequestId());
        Assert.assertEquals("只能给自己的狗狗使用加急快递", otherBody.getError());
        Assert.assertEquals(1, countItem(user.getAccountId(), "item_express"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "use_item_express"));
    }

    @Test
    public void adoptPersistsDogAndPetProfileReturnsIt() {
        User user = user();
        LocalDate today = LocalDate.now();

        PetRequestDTO adopt = new PetRequestDTO();
        adopt.setPetAction(PetAction.ADOPT);
        adopt.setContent(new PetAdoptDTO("corgi", "小短腿"));

        new PetActionHandler().process(user, adopt);

        Response adoptResponse = ((EmbeddedChannel) user.getChannel()).readOutbound();
        Assert.assertEquals(MessageType.PET, adoptResponse.getType());
        PetResponseDTO adoptBody = (PetResponseDTO) adoptResponse.getBody();
        Assert.assertTrue(adoptBody.isSuccess());
        Assert.assertEquals(PetAction.ADOPT, adoptBody.getPetAction());

        PetProfileDTO adoptedProfile = (PetProfileDTO) adoptBody.getContent();
        Assert.assertEquals(1, adoptedProfile.getDogs().size());
        PetDogDTO adoptedDog = adoptedProfile.getDogs().get(0);
        Assert.assertEquals("小短腿", adoptedDog.getName());
        Assert.assertEquals("corgi", adoptedDog.getBreed());
        Assert.assertEquals(8, adoptedDog.getSpeed());
        Assert.assertEquals(12, adoptedDog.getStamina());
        Assert.assertEquals(10, adoptedDog.getBurst());
        Assert.assertEquals(10, adoptedDog.getWisdom());
        Assert.assertEquals(10, adoptedDog.getBond());
        Assert.assertEquals(10, adoptedDog.getEnergy());
        Assert.assertEquals(today.toString(), findDogEnergyDate(adoptedDog.getId()));

        PetRequestDTO profile = new PetRequestDTO();
        profile.setPetAction(PetAction.PET_PROFILE);
        new PetActionHandler().process(user, profile);

        PetResponseDTO profileBody = (PetResponseDTO) ((Response) ((EmbeddedChannel) user.getChannel()).readOutbound()).getBody();
        PetProfileDTO persistedProfile = (PetProfileDTO) profileBody.getContent();
        Assert.assertEquals(1, persistedProfile.getDogs().size());
        Assert.assertEquals(adoptedDog.getId(), persistedProfile.getDogs().get(0).getId());
    }

    @Test
    public void petProfileResetsExpiredDogEnergyBeforeReturning() {
        User user = user();
        LocalDate today = LocalDate.now();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        String yesterday = today.minusDays(1).toString();
        setDogEnergy(user.getAccountId(), dog.getId(), 2, yesterday);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(10, findDog(profile, dog.getId()).getEnergy());
        Assert.assertEquals(today.toString(), findDogEnergyDate(dog.getId()));
    }

    @Test
    public void petProfileDoesNotResetDogEnergyForToday() {
        User user = user();
        LocalDate today = LocalDate.now();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        String todayText = today.toString();
        setDogEnergy(user.getAccountId(), dog.getId(), 3, todayText);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(3, findDog(profile, dog.getId()).getEnergy());
        Assert.assertEquals(todayText, findDogEnergyDate(dog.getId()));
    }

    @Test
    public void petProfileUsesCurrentEnergyLimitWhenResettingExpiredDogEnergy() {
        User user = user();
        LocalDate today = LocalDate.now();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setEnergyLimit(user.getAccountId(), 11);
        setDogEnergy(user.getAccountId(), dog.getId(), 2, today.minusDays(1).toString());

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(11, findDog(profile, dog.getId()).getEnergy());
        Assert.assertEquals(today.toString(), findDogEnergyDate(dog.getId()));
    }

    @Test
    public void adoptAllowsSecondDogIntoKennelWhenActivitySlotIsFull() {
        User user = user();
        adoptDog(user, "corgi", "小短腿");

        PetRequestDTO adopt = new PetRequestDTO();
        adopt.setPetAction(PetAction.ADOPT);
        adopt.setContent(new PetAdoptDTO("golden", "金毛"));

        new PetActionHandler().process(user, adopt);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.ADOPT, body.getPetAction());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertEquals(2, profile.getDogs().size());
        Assert.assertEquals("golden", profile.getDogs().get(1).getBreed());
        Assert.assertEquals(2, requestProfile(user).getDogs().size());
    }

    @Test
    public void setCompanionPersistsSecondDogAndPetProfileReturnsIt() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        Assert.assertNotEquals(firstDog.getId(), secondDog.getId());

        PetRequestDTO request = setCompanionRequest(secondDog.getId(), 80001L);
        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.SET_COMPANION, body.getPetAction());
        Assert.assertEquals(Long.valueOf(80001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(secondDog.getId(), profile.getCompanionDogId());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(secondDog.getId(), persistedProfile.getCompanionDogId());
    }

    @Test
    public void setCompanionRejectsMissingOrOtherAccountDogWithoutChangingCurrentCompanion() {
        User owner = user();
        setAssets(owner.getAccountId(), 2300, 2);
        PetDogDTO ownerFirstDog = adoptDog(owner, "corgi", "小短腿");
        PetDogDTO ownerSecondDog = adoptDog(owner, "golden", "金毛");
        new PetActionHandler().process(owner, setCompanionRequest(ownerSecondDog.getId(), 80002L));
        Assert.assertTrue(readPetBody(owner).isSuccess());

        User other = user(2002L, "other_user");
        PetDogDTO otherDog = adoptDog(other, "poodle", "贵宾");

        new PetActionHandler().process(owner, setCompanionRequest("missing-dog", 80003L));
        PetResponseDTO missingBody = readPetBody(owner);
        Assert.assertFalse(missingBody.isSuccess());
        Assert.assertEquals(PetAction.SET_COMPANION, missingBody.getPetAction());
        Assert.assertEquals(Long.valueOf(80003L), missingBody.getRequestId());
        Assert.assertEquals("只能设置自己的狗狗", missingBody.getError());
        Assert.assertEquals(ownerSecondDog.getId(), requestProfile(owner).getCompanionDogId());

        new PetActionHandler().process(owner, setCompanionRequest(otherDog.getId(), 80004L));
        PetResponseDTO otherBody = readPetBody(owner);
        Assert.assertFalse(otherBody.isSuccess());
        Assert.assertEquals(PetAction.SET_COMPANION, otherBody.getPetAction());
        Assert.assertEquals(Long.valueOf(80004L), otherBody.getRequestId());
        Assert.assertEquals("只能设置自己的狗狗", otherBody.getError());
        Assert.assertEquals(ownerSecondDog.getId(), requestProfile(owner).getCompanionDogId());
    }

    @Test
    public void malformedSetCompanionContentReturnsPetFailureResponse() {
        User user = user();

        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SET_COMPANION);
        request.setRequestId(80005L);
        request.setContent("bad-content");

        new PetActionHandler().process(user, request);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.SET_COMPANION, body.getPetAction());
        Assert.assertEquals(Long.valueOf(80005L), body.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", body.getError());
    }

    @Test
    public void concurrentAdoptPersistsAllKennelDogs() throws Exception {
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<PetResponseDTO> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            final int index = i;
            executor.submit(() -> {
                User user = user();
                PetRequestDTO adopt = new PetRequestDTO();
                adopt.setPetAction(PetAction.ADOPT);
                adopt.setContent(new PetAdoptDTO("corgi", "狗" + index));
                ready.countDown();
                await(start);
                new PetActionHandler().process(user, adopt);
                responses.add(readPetBody(user));
            });
        }

        Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        int successCount = 0;
        for (PetResponseDTO response : responses) {
            if (response.isSuccess()) {
                successCount++;
            }
        }
        Assert.assertEquals(attempts, responses.size());
        Assert.assertEquals(attempts, successCount);
        Assert.assertEquals(attempts, requestProfile(user()).getDogs().size());
    }

    @Test
    public void renamePersistsOwnDogName() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");

        PetRequestDTO rename = new PetRequestDTO();
        rename.setPetAction(PetAction.RENAME);
        rename.setContent(new PetRenameDTO(dog.getId(), "闪电"));

        new PetActionHandler().process(user, rename);

        PetResponseDTO renameBody = readPetBody(user);
        Assert.assertTrue(renameBody.isSuccess());
        Assert.assertEquals(PetAction.RENAME, renameBody.getPetAction());
        PetProfileDTO renamedProfile = (PetProfileDTO) renameBody.getContent();
        Assert.assertEquals("闪电", renamedProfile.getDogs().get(0).getName());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals("闪电", persistedProfile.getDogs().get(0).getName());
    }

    @Test
    public void renameRejectsOtherAccountDog() {
        User owner = user();
        PetDogDTO dog = adoptDog(owner, "corgi", "小短腿");

        User other = user(2002L, "other_user");
        PetRequestDTO rename = new PetRequestDTO();
        rename.setPetAction(PetAction.RENAME);
        rename.setContent(new PetRenameDTO(dog.getId(), "偷改名"));

        new PetActionHandler().process(other, rename);

        PetResponseDTO renameBody = readPetBody(other);
        Assert.assertFalse(renameBody.isSuccess());
        Assert.assertEquals(PetAction.RENAME, renameBody.getPetAction());
        Assert.assertEquals("只能修改自己的狗狗", renameBody.getError());

        PetProfileDTO ownerProfile = requestProfile(owner);
        Assert.assertEquals("小短腿", ownerProfile.getDogs().get(0).getName());
    }

    @Test
    public void feedConsumesFoodAndUpdatesDogStatsPersistently() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");

        PetRequestDTO feed = new PetRequestDTO();
        feed.setPetAction(PetAction.FEED);
        feed.setContent(new PetFeedDTO(dog.getId()));

        new PetActionHandler().process(user, feed);

        PetResponseDTO feedBody = readPetBody(user);
        Assert.assertTrue(feedBody.isSuccess());
        Assert.assertEquals(PetAction.FEED, feedBody.getPetAction());
        PetProfileDTO fedProfile = (PetProfileDTO) feedBody.getContent();
        Assert.assertEquals(5, fedProfile.getAssets().getFood());
        Assert.assertEquals(11, fedProfile.getDogs().get(0).getBond());
        Assert.assertEquals(10, fedProfile.getDogs().get(0).getEnergy());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(5, persistedProfile.getAssets().getFood());
        Assert.assertEquals(11, persistedProfile.getDogs().get(0).getBond());
    }

    @Test
    public void feedRejectsWhenFoodIsEmptyWithoutChangingDog() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setFood(user.getAccountId(), 0);

        PetProfileDTO before = requestProfile(user);
        Assert.assertEquals(0, before.getAssets().getFood());
        Assert.assertEquals(10, before.getDogs().get(0).getBond());
        Assert.assertEquals(10, before.getDogs().get(0).getEnergy());

        new PetActionHandler().process(user, feedRequest(dog.getId()));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.FEED, body.getPetAction());
        Assert.assertEquals("狗粮不足", body.getError());

        PetProfileDTO after = requestProfile(user);
        Assert.assertEquals(0, after.getAssets().getFood());
        Assert.assertEquals(10, after.getDogs().get(0).getBond());
        Assert.assertEquals(10, after.getDogs().get(0).getEnergy());
    }

    @Test
    public void feedOnlyAddsBondOncePerDogPerDayAndStillAllowsFoodAfterEnergyRestoreLimit() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setFood(user.getAccountId(), 6);
        setDogEnergy(user.getAccountId(), dog.getId(), 4, LocalDate.now().toString());

        for (int i = 0; i < 5; i++) {
            new PetActionHandler().process(user, feedRequest(dog.getId()));
            Assert.assertTrue(readPetBody(user).isSuccess());
        }

        PetProfileDTO beforeSixth = requestProfile(user);
        Assert.assertEquals(1, beforeSixth.getAssets().getFood());
        Assert.assertEquals(11, findDog(beforeSixth, dog.getId()).getBond());
        Assert.assertEquals(9, findDog(beforeSixth, dog.getId()).getEnergy());

        new PetActionHandler().process(user, feedRequest(dog.getId()));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.FEED, body.getPetAction());

        PetProfileDTO afterSixth = requestProfile(user);
        Assert.assertEquals(0, afterSixth.getAssets().getFood());
        Assert.assertEquals(11, findDog(afterSixth, dog.getId()).getBond());
        Assert.assertEquals(9, findDog(afterSixth, dog.getId()).getEnergy());
    }

    @Test
    public void feedBondFirstTimeIsPerDogButEnergyRestoreLimitIsSharedByAccount() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        setFood(user.getAccountId(), 6);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        setDogEnergy(user.getAccountId(), firstDog.getId(), 5, LocalDate.now().toString());
        setDogEnergy(user.getAccountId(), secondDog.getId(), 5, LocalDate.now().toString());

        for (int i = 0; i < 3; i++) {
            new PetActionHandler().process(user, feedRequest(firstDog.getId()));
            Assert.assertTrue(readPetBody(user).isSuccess());
        }
        for (int i = 0; i < 2; i++) {
            new PetActionHandler().process(user, feedRequest(secondDog.getId()));
            Assert.assertTrue(readPetBody(user).isSuccess());
        }

        PetProfileDTO beforeSixth = requestProfile(user);
        Assert.assertEquals(1, beforeSixth.getAssets().getFood());
        Assert.assertEquals(11, findDog(beforeSixth, firstDog.getId()).getBond());
        Assert.assertEquals(8, findDog(beforeSixth, firstDog.getId()).getEnergy());
        Assert.assertEquals(15, findDog(beforeSixth, secondDog.getId()).getBond());
        Assert.assertEquals(7, findDog(beforeSixth, secondDog.getId()).getEnergy());

        new PetActionHandler().process(user, feedRequest(secondDog.getId()));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());

        PetProfileDTO afterSixth = requestProfile(user);
        Assert.assertEquals(0, afterSixth.getAssets().getFood());
        Assert.assertEquals(11, findDog(afterSixth, firstDog.getId()).getBond());
        Assert.assertEquals(8, findDog(afterSixth, firstDog.getId()).getEnergy());
        Assert.assertEquals(15, findDog(afterSixth, secondDog.getId()).getBond());
        Assert.assertEquals(7, findDog(afterSixth, secondDog.getId()).getEnergy());
    }

    @Test
    public void feedIgnoresYesterdayCounter() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertDailyCounter(user.getAccountId(), LocalDate.now().minusDays(1).toString(), "feed_food", 5);

        new PetActionHandler().process(user, feedRequest(dog.getId()));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(5, profile.getAssets().getFood());
        Assert.assertEquals(11, findDog(profile, dog.getId()).getBond());
    }

    @Test
    public void feedResetsExpiredDogEnergyBeforeAddingFoodEnergy() {
        User user = user();
        LocalDate today = LocalDate.now();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 2, today.minusDays(1).toString());

        new PetActionHandler().process(user, feedRequest(dog.getId()));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(5, profile.getAssets().getFood());
        Assert.assertEquals(11, findDog(profile, dog.getId()).getBond());
        Assert.assertEquals(10, findDog(profile, dog.getId()).getEnergy());
        Assert.assertEquals(today.toString(), findDogEnergyDate(dog.getId()));
    }

    @Test
    public void feedFailureForInsufficientFoodDoesNotConsumeDailyLimit() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setFood(user.getAccountId(), 0);

        new PetActionHandler().process(user, feedRequest(dog.getId()));
        PetResponseDTO emptyFoodBody = readPetBody(user);
        Assert.assertFalse(emptyFoodBody.isSuccess());
        Assert.assertEquals("狗粮不足", emptyFoodBody.getError());

        setFood(user.getAccountId(), 5);
        for (int i = 0; i < 5; i++) {
            new PetActionHandler().process(user, feedRequest(dog.getId()));
            Assert.assertTrue(readPetBody(user).isSuccess());
        }

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(0, profile.getAssets().getFood());
        Assert.assertEquals(11, findDog(profile, dog.getId()).getBond());
    }

    @Test
    public void malformedFeedContentReturnsPetFailureResponse() {
        User user = user();

        PetRequestDTO feed = new PetRequestDTO();
        feed.setPetAction(PetAction.FEED);
        feed.setRequestId(20002L);
        feed.setContent("bad-content");

        new PetActionHandler().process(user, feed);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.FEED, body.getPetAction());
        Assert.assertEquals(Long.valueOf(20002L), body.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", body.getError());
    }

    @Test
    public void seventhDayCheckinRewardsBonesAndTwoNormalItems() {
        User user = user(96001L, "seventh_checkin_user");
        seedSixHistoricalCheckins(user.getAccountId());

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.CHECKIN, body.getPetAction());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(400, profile.getAssets().getBones());
        Assert.assertEquals(2, totalInventoryCount(profile));
        assertOnlyNormalItems(profile);
        Assert.assertTrue(profile.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(7, profile.getCheckinStatus().getCycleDay());
        Assert.assertEquals(7, profile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(21, profile.getCheckinStatus().getMilestoneRemaining());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(400, persistedProfile.getAssets().getBones());
        Assert.assertEquals(2, totalInventoryCount(persistedProfile));
        assertOnlyNormalItems(persistedProfile);
        Assert.assertEquals(7, persistedProfile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(21, persistedProfile.getCheckinStatus().getMilestoneRemaining());
    }

    @Test
    public void seventhDayMakeupCheckinRewardsBonesAndTwoNormalItems() {
        User user = user(96002L, "seventh_makeup_user");
        setMakeupCards(user.getAccountId(), 1);
        seedSixHistoricalCheckins(user.getAccountId());
        String checkinDate = pastDateInCurrentMonthOrSkip();

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 96002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, body.getPetAction());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(400, profile.getAssets().getBones());
        Assert.assertEquals(0, profile.getAssets().getMakeupCards());
        Assert.assertEquals(2, totalInventoryCount(profile));
        assertOnlyNormalItems(profile);
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void seventhDayCheckinConvertsFullNormalItemRewardsToBones() {
        User user = user(96003L, "seventh_full_items_user");
        seedSixHistoricalCheckins(user.getAccountId());
        for (String itemId : normalLuckyBagItemIds()) {
            insertPetItem(user.getAccountId(), itemId, 9);
        }

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(420, profile.getAssets().getBones());
        Assert.assertEquals(normalLuckyBagItemIds().size() * 9, totalInventoryCount(profile));
        for (PetInventoryItemDTO item : profile.getItems()) {
            Assert.assertTrue(item.getCount() <= 9);
        }
    }

    @Test
    public void seventhDayCheckinRewardDoesNotTouchShopCounters() {
        User user = user(96004L, "seventh_counter_user");
        seedSixHistoricalCheckins(user.getAccountId());

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_normal_item_buy"));
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    @Test
    public void checkinCanOnlyRunOncePerDayAndRewardsFirstDay() {
        User user = user();
        adoptDog(user, "corgi", "小短腿");

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);

        new PetActionHandler().process(user, checkin);

        PetResponseDTO firstBody = readPetBody(user);
        Assert.assertTrue(firstBody.isSuccess());
        Assert.assertEquals(PetAction.CHECKIN, firstBody.getPetAction());
        PetProfileDTO firstProfile = (PetProfileDTO) firstBody.getContent();
        Assert.assertEquals(320, firstProfile.getAssets().getBones());
        Assert.assertEquals(6, firstProfile.getAssets().getFood());
        Assert.assertEquals(10, firstProfile.getDogs().get(0).getBond());
        Assert.assertTrue(firstProfile.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(1, firstProfile.getCheckinStatus().getCycleDay());
        Assert.assertEquals(1, firstProfile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(27, firstProfile.getCheckinStatus().getMilestoneRemaining());
        Assert.assertEquals(Collections.singletonList(LocalDate.now().toString()),
                firstProfile.getCheckinStatus().getCheckedDatesInMonth());

        new PetActionHandler().process(user, checkin);

        PetResponseDTO secondBody = readPetBody(user);
        Assert.assertFalse(secondBody.isSuccess());
        Assert.assertEquals(PetAction.CHECKIN, secondBody.getPetAction());
        Assert.assertEquals("今天已经签到过了", secondBody.getError());

        PetProfileDTO persistedProfile = requestProfile(user);
        Assert.assertEquals(320, persistedProfile.getAssets().getBones());
        Assert.assertEquals(10, persistedProfile.getDogs().get(0).getBond());
        Assert.assertTrue(persistedProfile.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(1, persistedProfile.getCheckinStatus().getCycleDay());
        Assert.assertEquals(1, persistedProfile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(27, persistedProfile.getCheckinStatus().getMilestoneRemaining());
        Assert.assertTrue(persistedProfile.getCheckinStatus().getCheckedDatesInMonth()
                .contains(LocalDate.now().toString()));
    }

    @Test
    public void checkinDoesNotRewardCurrentCompanionDogWhenSecondDogSelected() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        new PetActionHandler().process(user, setCompanionRequest(secondDog.getId(), 60017L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        PetProfileDTO before = requestProfile(user);

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
    }

    @Test
    public void checkinDoesNotFallbackBondWhenCompanionDogIdInvalid() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        setCompanionDogId(user.getAccountId(), "missing-dog");
        PetProfileDTO before = requestProfile(user);

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
    }

    @Test
    public void checkinDoesNotFallbackBondWhenCompanionDogIdBlank() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        PetProfileDTO before = requestProfile(user);

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
    }

    @Test
    public void checkinSucceedsWhenAccountHasNoDog() {
        User user = user();

        PetRequestDTO checkin = new PetRequestDTO();
        checkin.setPetAction(PetAction.CHECKIN);
        new PetActionHandler().process(user, checkin);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertTrue(profile.getCheckinStatus().isTodayCheckedIn());
    }

    @Test
    public void greetAllDogsDoesNotRequireCheckinAndRewardsEveryDogOnce() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        setDogStatus(user.getAccountId(), secondDog.getId(), "exploring");
        PetProfileDTO before = requestProfile(user);
        Assert.assertFalse(before.getCheckinStatus().isTodayCheckedIn());

        new PetActionHandler().process(user, greetAllDogsRequest(61001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.GREET_ALL_DOGS, body.getPetAction());
        Assert.assertEquals(Long.valueOf(61001L), body.getRequestId());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertFalse(after.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond() + 1,
                findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond() + 1,
                findDog(after, secondDog.getId()).getBond());
        Assert.assertEquals("exploring", findDog(after, secondDog.getId()).getStatus());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_greet:" + firstDog.getId()));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_greet:" + secondDog.getId()));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_total:" + firstDog.getId()));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_total:" + secondDog.getId()));

        new PetActionHandler().process(user, greetAllDogsRequest(61002L));

        PetResponseDTO repeatedBody = readPetBody(user);
        Assert.assertTrue(repeatedBody.isSuccess());
        PetProfileDTO repeated = (PetProfileDTO) repeatedBody.getContent();
        Assert.assertEquals(findDog(after, firstDog.getId()).getBond(),
                findDog(repeated, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(after, secondDog.getId()).getBond(),
                findDog(repeated, secondDog.getId()).getBond());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_greet:" + firstDog.getId()));
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "bond_greet:" + secondDog.getId()));
    }

    @Test
    public void greetAllDogsRespectsPerDogDailyBondLimit() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDailyCounter(user.getAccountId(), "bond_total:" + dog.getId(), 4);
        PetProfileDTO before = requestProfile(user);

        new PetActionHandler().process(user, greetAllDogsRequest(61003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.GREET_ALL_DOGS, body.getPetAction());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, dog.getId()).getBond(), findDog(after, dog.getId()).getBond());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "bond_greet:" + dog.getId()));
        Assert.assertEquals(4, countDailyCounter(user.getAccountId(), "bond_total:" + dog.getId()));
    }

    @Test
    public void petProfileReturnsNextCycleDayAndCurrentMonthCheckinDatesWhenTodayNotChecked() {
        User user = user();
        String currentMonthPastDate = pastDateInCurrentMonthOrSkip();
        String previousMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString();
        insertCheckin(user.getAccountId(), currentMonthPastDate, 1);
        insertCheckin(user.getAccountId(), previousMonthDate, 2);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(LocalDate.now().toString(), profile.getCheckinStatus().getServerDate());
        Assert.assertFalse(profile.getCheckinStatus().isTodayCheckedIn());
        Assert.assertEquals(3, profile.getCheckinStatus().getCycleDay());
        Assert.assertEquals(2, profile.getCheckinStatus().getTotalCheckins());
        Assert.assertEquals(26, profile.getCheckinStatus().getMilestoneRemaining());
        Assert.assertEquals(Collections.singletonList(currentMonthPastDate),
                profile.getCheckinStatus().getCheckedDatesInMonth());
        Assert.assertFalse(profile.getCheckinStatus().getCheckedDatesInMonth().contains(previousMonthDate));
    }

    @Test
    public void makeupCheckinConsumesCardRewardsAndPersistsPastMissedDate() {
        User user = user();
        adoptDog(user, "corgi", "小短腿");
        setMakeupCards(user.getAccountId(), 1);
        String checkinDate = pastDateInCurrentMonthOrSkip();

        PetRequestDTO makeup = makeupCheckinRequest(checkinDate, 60006L);
        new PetActionHandler().process(user, makeup);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, body.getPetAction());
        Assert.assertEquals(Long.valueOf(60006L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(0, profile.getAssets().getMakeupCards());
        Assert.assertEquals(320, profile.getAssets().getBones());
        Assert.assertEquals(10, profile.getDogs().get(0).getBond());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinDoesNotRewardCurrentCompanionDogWhenSecondDogSelected() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        new PetActionHandler().process(user, setCompanionRequest(secondDog.getId(), 60018L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setMakeupCards(user.getAccountId(), 1);
        String checkinDate = pastDateInCurrentMonthOrSkip();
        PetProfileDTO before = requestProfile(user);

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60019L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinDoesNotFallbackBondWhenCompanionDogIdBlank() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        setMakeupCards(user.getAccountId(), 1);
        String checkinDate = pastDateInCurrentMonthOrSkip();
        PetProfileDTO before = requestProfile(user);

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60021L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinDoesNotFallbackBondWhenCompanionDogIdInvalid() {
        User user = user();
        setAssets(user.getAccountId(), 2300, 2);
        PetDogDTO firstDog = adoptDog(user, "corgi", "小短腿");
        PetDogDTO secondDog = adoptDog(user, "golden", "金毛");
        setCompanionDogId(user.getAccountId(), "missing-dog");
        setMakeupCards(user.getAccountId(), 1);
        String checkinDate = pastDateInCurrentMonthOrSkip();
        PetProfileDTO before = requestProfile(user);

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60022L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO after = (PetProfileDTO) body.getContent();
        Assert.assertEquals(findDog(before, firstDog.getId()).getBond(), findDog(after, firstDog.getId()).getBond());
        Assert.assertEquals(findDog(before, secondDog.getId()).getBond(), findDog(after, secondDog.getId()).getBond());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinSucceedsWhenAccountHasNoDog() {
        User user = user();
        setMakeupCards(user.getAccountId(), 1);
        String checkinDate = pastDateInCurrentMonthOrSkip();

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60020L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinRejectsWhenNoMakeupCardWithoutSideEffects() {
        User user = user();
        adoptDog(user, "corgi", "小短腿");
        String checkinDate = pastDateInCurrentMonthOrSkip();
        PetProfileDTO before = requestProfile(user);

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60007L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, body.getPetAction());
        Assert.assertEquals(Long.valueOf(60007L), body.getRequestId());
        Assert.assertEquals("补签卡不足", body.getError());
        PetProfileDTO after = requestProfile(user);
        Assert.assertEquals(before.getAssets().getMakeupCards(), after.getAssets().getMakeupCards());
        Assert.assertEquals(before.getAssets().getBones(), after.getAssets().getBones());
        Assert.assertEquals(before.getDogs().get(0).getBond(), after.getDogs().get(0).getBond());
        Assert.assertEquals(0, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinRejectsAlreadyCheckedDateWithoutDuplicateReward() {
        User user = user();
        adoptDog(user, "corgi", "小短腿");
        setMakeupCards(user.getAccountId(), 2);
        String checkinDate = pastDateInCurrentMonthOrSkip();
        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60008L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        PetProfileDTO beforeSecond = requestProfile(user);

        new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60009L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, body.getPetAction());
        Assert.assertEquals(Long.valueOf(60009L), body.getRequestId());
        Assert.assertEquals("该日期已经签到过了", body.getError());
        PetProfileDTO afterSecond = requestProfile(user);
        Assert.assertEquals(beforeSecond.getAssets().getMakeupCards(), afterSecond.getAssets().getMakeupCards());
        Assert.assertEquals(beforeSecond.getAssets().getBones(), afterSecond.getAssets().getBones());
        Assert.assertEquals(beforeSecond.getDogs().get(0).getBond(), afterSecond.getDogs().get(0).getBond());
        Assert.assertEquals(1, countCheckins(user.getAccountId(), checkinDate));
    }

    @Test
    public void makeupCheckinRejectsTodayOrFutureDate() {
        User user = user();
        setMakeupCards(user.getAccountId(), 2);

        new PetActionHandler().process(user, makeupCheckinRequest(LocalDate.now().toString(), 60010L));
        PetResponseDTO todayBody = readPetBody(user);
        Assert.assertFalse(todayBody.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, todayBody.getPetAction());
        Assert.assertEquals(Long.valueOf(60010L), todayBody.getRequestId());
        Assert.assertEquals("只能补签当前月份内早于今天的日期", todayBody.getError());

        String futureDate = LocalDate.now().plusDays(1).toString();
        new PetActionHandler().process(user, makeupCheckinRequest(futureDate, 60011L));
        PetResponseDTO futureBody = readPetBody(user);
        Assert.assertFalse(futureBody.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, futureBody.getPetAction());
        Assert.assertEquals(Long.valueOf(60011L), futureBody.getRequestId());
        Assert.assertEquals("只能补签当前月份内早于今天的日期", futureBody.getError());
        Assert.assertEquals(2, requestProfile(user).getAssets().getMakeupCards());
    }

    @Test
    public void makeupCheckinRejectsBlankInvalidAndPreviousMonthDateWithoutSideEffects() {
        User user = user();
        setMakeupCards(user.getAccountId(), 3);

        new PetActionHandler().process(user, makeupCheckinRequest("", 60014L));
        PetResponseDTO blankBody = readPetBody(user);
        Assert.assertFalse(blankBody.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, blankBody.getPetAction());
        Assert.assertEquals(Long.valueOf(60014L), blankBody.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", blankBody.getError());

        new PetActionHandler().process(user, makeupCheckinRequest("2026-02-30", 60015L));
        PetResponseDTO invalidBody = readPetBody(user);
        Assert.assertFalse(invalidBody.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, invalidBody.getPetAction());
        Assert.assertEquals(Long.valueOf(60015L), invalidBody.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", invalidBody.getError());

        String previousMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString();
        new PetActionHandler().process(user, makeupCheckinRequest(previousMonthDate, 60016L));
        PetResponseDTO previousMonthBody = readPetBody(user);
        Assert.assertFalse(previousMonthBody.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, previousMonthBody.getPetAction());
        Assert.assertEquals(Long.valueOf(60016L), previousMonthBody.getRequestId());
        Assert.assertEquals("只能补签当前月份内早于今天的日期", previousMonthBody.getError());

        PetProfileDTO profile = requestProfile(user);
        Assert.assertEquals(3, profile.getAssets().getMakeupCards());
        Assert.assertEquals(300, profile.getAssets().getBones());
        Assert.assertEquals(0, countAllCheckins(user.getAccountId()));
    }

    @Test
    public void malformedMakeupCheckinContentReturnsPetFailureResponse() {
        User user = user();

        PetRequestDTO makeup = new PetRequestDTO();
        makeup.setPetAction(PetAction.MAKEUP_CHECKIN);
        makeup.setRequestId(60012L);
        makeup.setContent("bad-content");

        new PetActionHandler().process(user, makeup);

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.MAKEUP_CHECKIN, body.getPetAction());
        Assert.assertEquals(Long.valueOf(60012L), body.getRequestId());
        Assert.assertEquals("狗狗请求内容无效", body.getError());
    }

    @Test
    public void concurrentMakeupCheckinAllowsOnlyOneSuccessForSameDate() throws Exception {
        int attempts = 8;
        setMakeupCards(user().getAccountId(), attempts);
        String checkinDate = pastDateInCurrentMonthOrSkip();
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<PetResponseDTO> responses = Collections.synchronizedList(new ArrayList<>());

        // 当前公开入口会先走同账号 JVM 锁，覆盖同进程并发不重复扣卡/发奖。
        // DB unique 约束异常仍保留在生产代码中，作为多进程或异常竞态的兜底转换。
        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                User user = user();
                ready.countDown();
                await(start);
                new PetActionHandler().process(user, makeupCheckinRequest(checkinDate, 60013L));
                responses.add(readPetBody(user));
            });
        }

        Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        int successCount = 0;
        int duplicateCount = 0;
        for (PetResponseDTO response : responses) {
            if (response.isSuccess()) {
                successCount++;
            } else if ("该日期已经签到过了".equals(response.getError())) {
                duplicateCount++;
            }
            Assert.assertEquals(Long.valueOf(60013L), response.getRequestId());
        }
        Assert.assertEquals(attempts, responses.size());
        Assert.assertEquals(1, successCount);
        Assert.assertEquals(attempts - 1, duplicateCount);
        PetProfileDTO profile = requestProfile(user());
        Assert.assertEquals(attempts - 1, profile.getAssets().getMakeupCards());
        Assert.assertEquals(320, profile.getAssets().getBones());
        Assert.assertEquals(1, countCheckins(user().getAccountId(), checkinDate));
    }

    @Test
    public void exploreStartSucceedsWithBackHillOneHourAndConsumesEnergy() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 5, LocalDate.now().toString());
        long before = System.currentTimeMillis();

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97001L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97001L), body.getRequestId());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        PetDogDTO exploringDog = findDog(profile, dog.getId());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertEquals("back_hill", exploringDog.getExploreLocation());
        Assert.assertTrue(exploringDog.getExploreEndsAt() >= before + TimeUnit.HOURS.toMillis(1));
        Assert.assertTrue(exploringDog.getExploreEndsAt() <= System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
        Assert.assertEquals(3, exploringDog.getEnergy());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartRejectsCreekBeforeBackHillCompletionCount() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 5, LocalDate.now().toString());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "creek", 1, 97043L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97043L), body.getRequestId());
        Assert.assertEquals("完成后山探险 3 次后才能进入小溪", body.getError());
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertEquals(5, persistedDog.getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartAllowsCreekAfterBackHillCompletionCount() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 5, LocalDate.now().toString());
        insertDailyCounter(user.getAccountId(), "lifetime", "explore_complete_back_hill", 3);

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "creek", 1, 97044L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97044L), body.getRequestId());
        PetDogDTO exploringDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertEquals("creek", exploringDog.getExploreLocation());
        Assert.assertEquals(3, exploringDog.getEnergy());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void petProfileReportsExploreCompletionCountersForLocationUnlockProgress() {
        User user = user();
        insertDailyCounter(user.getAccountId(), "lifetime", "explore_complete_back_hill", 2);
        insertDailyCounter(user.getAccountId(), "lifetime", "explore_complete_creek", 1);

        PetProfileDTO profile = requestProfile(user);

        Assert.assertEquals(2, profile.getExploreStatus().getBackHillCompletions());
        Assert.assertEquals(1, profile.getExploreStatus().getCreekCompletions());
    }

    @Test
    public void exploreStartAllowsAdultEightHoursAndOpenUsesEightHourRewards() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogStage(user.getAccountId(), dog.getId(), "adult");
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());
        long before = System.currentTimeMillis();

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 8, 97019L));

        PetResponseDTO startBody = readPetBody(user);
        Assert.assertTrue(startBody.isSuccess());
        PetDogDTO exploringDog = findDog((PetProfileDTO) startBody.getContent(), dog.getId());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertTrue(exploringDog.getExploreEndsAt() >= before + TimeUnit.HOURS.toMillis(8));
        Assert.assertTrue(exploringDog.getExploreEndsAt() <= System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8));
        Assert.assertEquals(6, exploringDog.getEnergy());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "explore_start"));

        setExploreEnded(user.getAccountId(), dog.getId());
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97020L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(4, rewards.size());
            Assert.assertEquals(390, profile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void exploreStartAllowsChampionTwelveHoursAndOpenUsesTwelveHourRewards() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogStage(user.getAccountId(), dog.getId(), "champion");
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());
        long before = System.currentTimeMillis();

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 12, 97021L));

        PetResponseDTO startBody = readPetBody(user);
        Assert.assertTrue(startBody.isSuccess());
        PetDogDTO exploringDog = findDog((PetProfileDTO) startBody.getContent(), dog.getId());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertTrue(exploringDog.getExploreEndsAt() >= before + TimeUnit.HOURS.toMillis(12));
        Assert.assertTrue(exploringDog.getExploreEndsAt() <= System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12));
        Assert.assertEquals(5, exploringDog.getEnergy());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "explore_start"));

        setExploreEnded(user.getAccountId(), dog.getId());
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97022L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(5, rewards.size());
            Assert.assertEquals(420, profile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void exploreStartPromotesQualifiedPuppyBeforeCheckingEightHourUnlock() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "puppy",
                30, 30, 30, 30, 30, 3, 0);
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 8, 97025L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97025L), body.getRequestId());
        PetDogDTO exploringDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("adult", exploringDog.getStage());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertEquals(6, exploringDog.getEnergy());
    }

    @Test
    public void exploreStartPromotesQualifiedAdultBeforeCheckingTwelveHourUnlock() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogGrowthProgress(user.getAccountId(), dog.getId(), "adult",
                60, 60, 60, 60, 60, 3, 1);
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 12, 97026L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97026L), body.getRequestId());
        PetDogDTO exploringDog = findDog((PetProfileDTO) body.getContent(), dog.getId());
        Assert.assertEquals("champion", exploringDog.getStage());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertEquals(5, exploringDog.getEnergy());
    }

    @Test
    public void exploreStartRejectsLockedLongDurationsWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 8, 97023L));
        PetResponseDTO puppyBody = readPetBody(user);
        Assert.assertFalse(puppyBody.isSuccess());
        Assert.assertEquals("成犬后才能派遣 8 小时探险", puppyBody.getError());

        setDogStage(user.getAccountId(), dog.getId(), "adult");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 12, 97024L));
        PetResponseDTO adultBody = readPetBody(user);
        Assert.assertFalse(adultBody.isSuccess());
        Assert.assertEquals("冠军犬才能派遣 12 小时探险", adultBody.getError());

        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(10, persistedDog.getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartRejectsWhenEnergyNotEnoughWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 1, LocalDate.now().toString());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97002L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals("狗狗活力不足", body.getError());
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(1, persistedDog.getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartRejectsWhenDogIsNotIdleWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogStatus(user.getAccountId(), dog.getId(), "racing");

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97003L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals("只有空闲狗狗可以去探险", body.getError());
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("racing", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(10, persistedDog.getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartRejectsDailyLimitInvalidLocationDurationAndForeignDogWithoutSideEffects() {
        User user = user(97004L, "explore_limit_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertDailyCounter(user.getAccountId(), LocalDate.now().toString(), "explore_start", 3);

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97004L));
        PetResponseDTO limitBody = readPetBody(user);
        Assert.assertFalse(limitBody.isSuccess());
        Assert.assertEquals("今日探险派遣次数已达上限", limitBody.getError());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "front_yard", 1, 97005L));
        PetResponseDTO locationBody = readPetBody(user);
        Assert.assertFalse(locationBody.isSuccess());
        Assert.assertEquals("暂不支持该探险地点", locationBody.getError());

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 2, 97006L));
        PetResponseDTO durationBody = readPetBody(user);
        Assert.assertFalse(durationBody.isSuccess());
        Assert.assertEquals("暂不支持该探险时长", durationBody.getError());

        User owner = user(97005L, "explore_owner");
        PetDogDTO ownerDog = adoptDog(owner, "golden", "金毛");
        new PetActionHandler().process(user, exploreStartRequest(ownerDog.getId(), "back_hill", 1, 97007L));
        PetResponseDTO foreignBody = readPetBody(user);
        Assert.assertFalse(foreignBody.isSuccess());
        Assert.assertEquals("只能派遣自己的狗狗探险", foreignBody.getError());

        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(10, persistedDog.getEnergy());
        Assert.assertEquals(3, countDailyCounter(user.getAccountId(), "explore_start"));
    }

    @Test
    public void exploreStartRejectsWhenBackHillChestInventoryIsFullWithoutSideEffects() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 5, LocalDate.now().toString());
        insertPetItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID, 99);

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97041L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_START, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97041L), body.getRequestId());
        Assert.assertEquals("后山箱子已达上限，打开后再探险", body.getError());
        PetDogDTO persistedDog = findDog(requestProfile(user), dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
        Assert.assertEquals(5, persistedDog.getEnergy());
        Assert.assertEquals(0, countDailyCounter(user.getAccountId(), "explore_start"));
        Assert.assertEquals(99, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
    }

    @Test
    public void petProfileSettlesEndedBackHillExploreIntoChestAndIdleDog() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97042L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());

        PetProfileDTO profile = requestProfile(user);

        PetDogDTO settledDog = findDog(profile, dog.getId());
        Assert.assertEquals("idle", settledDog.getStatus());
        Assert.assertNull(settledDog.getExploreLocation());
        Assert.assertNull(settledDog.getExploreEndsAt());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
        Assert.assertEquals(1, findInventoryCount(profile, BACK_HILL_CHEST_ITEM_ID));
        Assert.assertEquals(300, profile.getAssets().getBones());
    }

    @Test
    public void petProfileSettlesEndedCreekExploreIntoCreekChestAndIdleDog() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertDailyCounter(user.getAccountId(), "lifetime", "explore_complete_back_hill", 3);
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "creek", 1, 97047L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());

        PetProfileDTO profile = requestProfile(user);

        PetDogDTO settledDog = findDog(profile, dog.getId());
        Assert.assertEquals("idle", settledDog.getStatus());
        Assert.assertNull(settledDog.getExploreLocation());
        Assert.assertNull(settledDog.getExploreEndsAt());
        Assert.assertEquals(0, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
        Assert.assertEquals(1, countItem(user.getAccountId(), CREEK_CHEST_ITEM_ID));
        Assert.assertEquals(1, findInventoryCount(profile, CREEK_CHEST_ITEM_ID));
        Assert.assertEquals(1, countCounter(user.getAccountId(), "lifetime", "explore_complete_creek"));
    }

    @Test
    public void exploreOpenRejectsBeforeEndsAtWithoutSettlement() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97008L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        PetProfileDTO beforeOpen = requestProfile(user);

        new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97009L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_OPEN, body.getPetAction());
        Assert.assertEquals("探险还没有结束，请稍后再来开箱", body.getError());
        PetProfileDTO afterOpen = requestProfile(user);
        Assert.assertEquals(beforeOpen.getAssets().getBones(), afterOpen.getAssets().getBones());
        Assert.assertEquals(totalInventoryCount(beforeOpen), totalInventoryCount(afterOpen));
        Assert.assertEquals("exploring", findDog(afterOpen, dog.getId()).getStatus());
    }

    @Test
    public void exploreOpenAfterEndsAtReturnsIdleAndRewardsAtLeastBaseBones() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97010L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());

        new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97011L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_OPEN, body.getPetAction());
        JSONObject result = JSONUtil.parseObj(body.getContent());
        PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
        JSONArray rewards = result.getJSONArray("rewards");
        Assert.assertTrue(rewards.size() >= 1);
        PetDogDTO openedDog = findDog(profile, dog.getId());
        Assert.assertEquals("idle", openedDog.getStatus());
        Assert.assertNull(openedDog.getExploreLocation());
        Assert.assertNull(openedDog.getExploreEndsAt());
        Assert.assertTrue(profile.getAssets().getBones() >= 310);
    }

    @Test
    public void exploreOpenDiscoversBackHillCollectionAndReturnsCollectionReward() {
        User user = user(97031L, "explore_collection_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97031L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 58);

        try {
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97032L));

            PetResponseDTO body = readPetBody(user);
            Assert.assertTrue(body.isSuccess());
            JSONObject result = JSONUtil.parseObj(body.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(1, profile.getCollections().size());
            PetCollectionItemDTO collection = profile.getCollections().get(0);
            Assert.assertTrue(backHillCollectionItemIds().contains(collection.getItemId()));
            Assert.assertEquals(1, collection.getCount());
            Assert.assertTrue(collection.isDiscovered());
            Assert.assertTrue(hasReward(rewards, "collection", collection.getItemId()));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void exploreOpenUnlocksMysteryCaveButNotHuskyAfterThreeTreasureMapFragments() {
        User user = user(97033L, "explore_husky_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 78);
        IntSupplier originalEasterEventSupplier = setExploreEasterEventSupplier(() -> 1);

        try {
            openEndedOneHourExplore(user, dog.getId(), 97033L, 97034L);
            openEndedOneHourExplore(user, dog.getId(), 97035L, 97036L);
            openEndedOneHourExplore(user, dog.getId(), 97037L, 97038L);

            PetProfileDTO profile = requestProfile(user);
            Assert.assertEquals(3, profile.getExploreStatus().getTreasureMapFragments());
            Assert.assertTrue(profile.getExploreStatus().isMysteryCaveUnlocked());
            Assert.assertFalse(profile.getExploreStatus().isMysteryCaveCompleted());
            Assert.assertFalse(profile.getExploreStatus().isHuskyUnlocked());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
            setExploreEasterEventSupplier(originalEasterEventSupplier);
        }
    }

    @Test
    public void mysteryCaveRequiresFragmentsAndCompletesHuskyUnlockOnce() {
        User user = user(97034L, "mystery_cave_user");
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        setDogEnergy(user.getAccountId(), dog.getId(), 10, LocalDate.now().toString());
        insertPetCollection(user.getAccountId(), "treasure_map_fragment", 2, true);

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "mystery_cave", 8, 97043L));
        PetResponseDTO lockedBody = readPetBody(user);
        Assert.assertFalse(lockedBody.isSuccess());
        Assert.assertEquals("集齐 3 张藏宝图碎片后才能进入神秘洞穴", lockedBody.getError());

        insertPetCollection(user.getAccountId(), "treasure_map_fragment", 3, true);

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "mystery_cave", 8, 97044L));
        PetResponseDTO startBody = readPetBody(user);
        Assert.assertTrue(startBody.isSuccess());
        PetDogDTO exploringDog = findDog((PetProfileDTO) startBody.getContent(), dog.getId());
        Assert.assertEquals("exploring", exploringDog.getStatus());
        Assert.assertEquals("mystery_cave", exploringDog.getExploreLocation());
        Assert.assertEquals(6, exploringDog.getEnergy());

        setExploreEnded(user.getAccountId(), dog.getId());
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97045L));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            Assert.assertTrue(profile.getExploreStatus().isMysteryCaveUnlocked());
            Assert.assertTrue(profile.getExploreStatus().isMysteryCaveCompleted());
            Assert.assertTrue(profile.getExploreStatus().isHuskyUnlocked());
            Assert.assertNotNull(findCollection(profile, "mystery_cave_completed"));
            Assert.assertEquals(0, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }

        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "mystery_cave", 8, 97046L));
        PetResponseDTO repeatedBody = readPetBody(user);
        Assert.assertFalse(repeatedBody.isSuccess());
        Assert.assertEquals("神秘洞穴已经完成", repeatedBody.getError());
    }

    @Test
    public void exploreOpenResetsInvalidExploreWithoutRewards() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97017L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        clearExploreEndsAt(user.getAccountId(), dog.getId());
        PetProfileDTO beforeOpen = requestProfile(user);

        new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97018L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_OPEN, body.getPetAction());
        Assert.assertEquals(Long.valueOf(97018L), body.getRequestId());
        Assert.assertEquals("探险数据异常，已重置，请重新开始探险", body.getError());
        PetProfileDTO afterOpen = requestProfile(user);
        Assert.assertEquals(beforeOpen.getAssets().getBones(), afterOpen.getAssets().getBones());
        Assert.assertEquals(totalInventoryCount(beforeOpen), totalInventoryCount(afterOpen));
        PetDogDTO persistedDog = findDog(afterOpen, dog.getId());
        Assert.assertEquals("idle", persistedDog.getStatus());
        Assert.assertNull(persistedDog.getExploreLocation());
        Assert.assertNull(persistedDog.getExploreEndsAt());
    }

    @Test
    public void exploreOpenRejectsRepeatedOpenWithoutMoreRewards() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 1, 97012L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());
        new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97013L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        PetProfileDTO afterFirstOpen = requestProfile(user);

        new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97014L));

        PetResponseDTO body = readPetBody(user);
        Assert.assertFalse(body.isSuccess());
        Assert.assertEquals(PetAction.EXPLORE_OPEN, body.getPetAction());
        Assert.assertEquals("狗狗当前没有正在等待开箱的探险", body.getError());
        PetProfileDTO afterSecondOpen = requestProfile(user);
        Assert.assertEquals(afterFirstOpen.getAssets().getBones(), afterSecondOpen.getAssets().getBones());
        Assert.assertEquals(totalInventoryCount(afterFirstOpen), totalInventoryCount(afterSecondOpen));
    }

    @Test
    public void exploreOpenConvertsItemRewardsToBonesWhenDailyItemGainLimitReached() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        new PetActionHandler().process(user, exploreStartRequest(dog.getId(), "back_hill", 4, 97015L));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dog.getId());
        setDailyCounter(user.getAccountId(), "explore_item_gain", 5);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 0);

        try {
            new PetActionHandler().process(user, exploreOpenRequest(dog.getId(), 97016L));

            PetResponseDTO body = readPetBody(user);
            Assert.assertTrue(body.isSuccess());
            JSONObject result = JSONUtil.parseObj(body.getContent());
            JSONArray rewards = result.getJSONArray("rewards");
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            Assert.assertEquals(345, profile.getAssets().getBones());
            Assert.assertEquals(3, rewards.size());
            for (int i = 0; i < rewards.size(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                Assert.assertEquals("bones", reward.getStr("type"));
            }
            Assert.assertEquals(0, totalInventoryCount(profile));
            Assert.assertEquals(5, countDailyCounter(user.getAccountId(), "explore_item_gain"));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void useItemBackHillChestConsumesInventoryAndReturnsExploreRewardsWithoutExploringDog() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertPetItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID, 1);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);

        try {
            new PetActionHandler().process(user, useItemRequest(BACK_HILL_CHEST_ITEM_ID, null, 97043L));

            PetResponseDTO body = readPetBody(user);
            Assert.assertTrue(body.isSuccess());
            Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
            Assert.assertEquals(Long.valueOf(97043L), body.getRequestId());
            JSONObject result = JSONUtil.parseObj(body.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(2, rewards.size());
            Assert.assertEquals(0, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));
            Assert.assertEquals(0, findInventoryCount(profile, BACK_HILL_CHEST_ITEM_ID));
            Assert.assertEquals("idle", findDog(profile, dog.getId()).getStatus());
            Assert.assertEquals(325, profile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    @Test
    public void useItemCreekChestConsumesInventoryAndReturnsCreekCollectionReward() {
        User user = user();
        PetDogDTO dog = adoptDog(user, "corgi", "小短腿");
        insertPetItem(user.getAccountId(), CREEK_CHEST_ITEM_ID, 1);
        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 58);

        try {
            new PetActionHandler().process(user, useItemRequest(CREEK_CHEST_ITEM_ID, null, 97048L));

            PetResponseDTO body = readPetBody(user);
            Assert.assertTrue(body.isSuccess());
            Assert.assertEquals(PetAction.USE_ITEM, body.getPetAction());
            Assert.assertEquals(Long.valueOf(97048L), body.getRequestId());
            JSONObject result = JSONUtil.parseObj(body.getContent());
            PetProfileDTO profile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(0, countItem(user.getAccountId(), CREEK_CHEST_ITEM_ID));
            Assert.assertEquals(0, findInventoryCount(profile, CREEK_CHEST_ITEM_ID));
            Assert.assertEquals("idle", findDog(profile, dog.getId()).getStatus());
            Assert.assertEquals(310, profile.getAssets().getBones());
            Assert.assertEquals(1, profile.getCollections().size());
            PetCollectionItemDTO collection = profile.getCollections().get(0);
            Assert.assertTrue(creekCollectionItemIds().contains(collection.getItemId()));
            Assert.assertTrue(hasReward(rewards, "collection", collection.getItemId()));
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    private static User user() {
        return user(1001L, "dog_user");
    }

    private static User user(long accountId, String account) {
        User user = new User();
        user.setId("desktop-channel");
        user.setAccountId(accountId);
        user.setAccount(account);
        user.setNickname("养狗人");
        user.setStatus(UserStatus.FISHING);
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static PetDogDTO adoptDog(User user, String breed, String name) {
        PetRequestDTO adopt = new PetRequestDTO();
        adopt.setPetAction(PetAction.ADOPT);
        adopt.setContent(new PetAdoptDTO(breed, name));

        new PetActionHandler().process(user, adopt);

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        List<PetDogDTO> dogs = ((PetProfileDTO) body.getContent()).getDogs();
        return dogs.get(dogs.size() - 1);
    }

    private static PetProfileDTO requestProfile(User user) {
        PetRequestDTO profile = new PetRequestDTO();
        profile.setPetAction(PetAction.PET_PROFILE);
        new PetActionHandler().process(user, profile);
        return (PetProfileDTO) readPetBody(user).getContent();
    }

    private static PetDogDTO findDog(PetProfileDTO profile, String dogId) {
        for (PetDogDTO dog : profile.getDogs()) {
            if (dog.getId().equals(dogId)) {
                return dog;
            }
        }
        throw new AssertionError("未找到狗狗：" + dogId);
    }

    private static PetRequestDTO feedRequest(String dogId) {
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.FEED);
        request.setContent(new PetFeedDTO(dogId));
        return request;
    }

    private static PetRequestDTO greetAllDogsRequest(long requestId) {
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.GREET_ALL_DOGS);
        request.setRequestId(requestId);
        return request;
    }

    private static PetRequestDTO exploreStartRequest(String dogId, String location, int durationHours, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("dogId", dogId);
        content.put("location", location);
        content.put("durationHours", durationHours);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.EXPLORE_START);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO exploreOpenRequest(String dogId, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("dogId", dogId);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.EXPLORE_OPEN);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO trainingSkillRequest(PetAction action, String skillId, String dogId, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("skillId", skillId);
        content.put("dogId", dogId);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(action);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetResponseDTO handlerProcess(User user, PetRequestDTO request) {
        new PetActionHandler().process(user, request);
        return readPetBody(user);
    }

    private static PetProfileDTO parseProfile(PetResponseDTO body) {
        Assert.assertTrue(body.isSuccess());
        return (PetProfileDTO) body.getContent();
    }

    private static PetExploreOpenResultDTO parseExploreOpenResult(Object content) {
        if (content instanceof PetExploreOpenResultDTO) {
            return (PetExploreOpenResultDTO) content;
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(content), PetExploreOpenResultDTO.class);
    }

    private static void openEndedOneHourExplore(User user, String dogId, long startRequestId, long openRequestId) {
        new PetActionHandler().process(user, exploreStartRequest(dogId, "back_hill", 1, startRequestId));
        Assert.assertTrue(readPetBody(user).isSuccess());
        setExploreEnded(user.getAccountId(), dogId);
        new PetActionHandler().process(user, exploreOpenRequest(dogId, openRequestId));
        Assert.assertTrue(readPetBody(user).isSuccess());
    }

    private static PetRequestDTO raceResultRequest(String dogId, int rank, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("dogId", dogId);
        content.put("rank", rank);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.RACE_RESULT);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetResponseDTO readPetBody(User user) {
        Response response = ((EmbeddedChannel) user.getChannel()).readOutbound();
        Assert.assertEquals(MessageType.PET, response.getType());
        return (PetResponseDTO) response.getBody();
    }

    private static void assertItem(PetInventoryItemDTO item, String itemId, int count) {
        Assert.assertEquals(itemId, item.getItemId());
        Assert.assertEquals(count, item.getCount());
    }

    private static void assertOnlyNormalItems(PetProfileDTO profile) {
        Set<String> normalItemIds = normalLuckyBagItemIds();
        for (PetInventoryItemDTO item : profile.getItems()) {
            Assert.assertTrue(normalItemIds.contains(item.getItemId()));
        }
    }

    private static void assertLuckyBagAwardsOnlyAvailableRarity(long accountId, Set<String> expectedItems,
                                                                Set<String> fullItemsA,
                                                                Set<String> fullItemsB) {
        User user = user(accountId, "lucky_bag_user_" + accountId);
        setAssets(user.getAccountId(), 300, 1);
        for (String itemId : fullItemsA) {
            insertPetItem(user.getAccountId(), itemId, 9);
        }
        for (String itemId : fullItemsB) {
            insertPetItem(user.getAccountId(), itemId, 9);
        }

        new PetActionHandler().process(user, shopBuyRequest("lucky_bag", 1, accountId));

        PetResponseDTO body = readPetBody(user);
        Assert.assertTrue(body.isSuccess());
        PetProfileDTO profile = (PetProfileDTO) body.getContent();
        Assert.assertEquals(50, profile.getAssets().getBones());
        PetInventoryItemDTO awardedItem = findAwardedItem(profile, expectedItems);
        Assert.assertNotNull(awardedItem);
        Assert.assertEquals(1, awardedItem.getCount());
        Assert.assertEquals(1, countDailyCounter(user.getAccountId(), "shop_lucky_bag_buy"));
    }

    private static PetInventoryItemDTO findAwardedItem(PetProfileDTO profile, Set<String> expectedItems) {
        for (PetInventoryItemDTO item : profile.getItems()) {
            if (expectedItems.contains(item.getItemId())) {
                return item;
            }
        }
        return null;
    }

    private static PetTrainingSkillDefinitionDTO findTrainingDefinition(PetProfileDTO profile, String skillId) {
        for (PetTrainingSkillDefinitionDTO definition : profile.getTrainingStatus().getDefinitions()) {
            if (definition.getSkillId().equals(skillId)) {
                return definition;
            }
        }
        throw new AssertionError("未找到训练技能定义：" + skillId);
    }

    private static PetTrainingSkillDTO findTrainingSkill(PetProfileDTO profile, String skillId) {
        for (PetTrainingSkillDTO skill : profile.getTrainingStatus().getSkills()) {
            if (skill.getSkillId().equals(skillId)) {
                return skill;
            }
        }
        throw new AssertionError("未找到训练技能：" + skillId);
    }

    private static int totalInventoryCount(PetProfileDTO profile) {
        int total = 0;
        for (PetInventoryItemDTO item : profile.getItems()) {
            total += item.getCount();
        }
        return total;
    }

    private static boolean hasReward(JSONArray rewards, String type, String itemId) {
        for (int i = 0; i < rewards.size(); i++) {
            JSONObject reward = rewards.getJSONObject(i);
            if (type.equals(reward.getStr("type")) && itemId.equals(reward.getStr("itemId"))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> backHillCollectionItemIds() {
        return new HashSet<>(Arrays.asList(
                "back_hill_ball",
                "back_hill_branch",
                "back_hill_leaf",
                "back_hill_stone",
                "back_hill_mushroom",
                "back_hill_feather"
        ));
    }

    private static Set<String> creekCollectionItemIds() {
        return new HashSet<>(Arrays.asList(
                "creek_shell",
                "creek_snail",
                "creek_lotus",
                "creek_duck",
                "creek_coral",
                "creek_drop"
        ));
    }

    private static Set<String> luckyBagItemIds() {
        Set<String> itemIds = new HashSet<>();
        itemIds.addAll(normalLuckyBagItemIds());
        itemIds.addAll(rareLuckyBagItemIds());
        itemIds.addAll(epicLuckyBagItemIds());
        return itemIds;
    }

    private static Set<String> normalLuckyBagItemIds() {
        return new HashSet<>(Arrays.asList(
                "item_mine_mark",
                "item_mine_area",
                "item_mine_scout",
                "item_mine_shield",
                "item_hint",
                "item_peek",
                "item_time",
                "item_inspiration",
                "item_sync_prophecy",
                "item_quiz_score_pad",
                "item_quiz_duel",
                "item_gomoku_prediction",
                "item_gomoku_review",
                "item_battle_echo",
                "item_battle_direct_hit",
                "item_prophecy"
        ));
    }

    private static Set<String> rareLuckyBagItemIds() {
        return new HashSet<>(Arrays.asList(
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
                "item_battle_airbag"
        ));
    }

    private static String dailyRareShopItemId(LocalDate date) {
        List<String> itemIds = Arrays.asList(
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
                "item_battle_airbag"
        );
        int itemIndex = (int) Math.floorMod(date.toEpochDay(), itemIds.size());
        return itemIds.get(itemIndex);
    }

    private static Set<String> epicLuckyBagItemIds() {
        return new HashSet<>(Arrays.asList(
                "item_wild_common",
                "item_party_equalizer",
                "item_gift_pack",
                "item_express",
                "item_lucky_day"
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static PetRequestDTO makeupCheckinRequest(String checkinDate, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("checkinDate", checkinDate);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.MAKEUP_CHECKIN);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO setCompanionRequest(String dogId, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("dogId", dogId);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SET_COMPANION);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO shopBuyRequest(String itemId, int quantity, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("itemId", itemId);
        content.put("quantity", quantity);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SHOP_BUY);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO sellItemRequest(String itemId, int quantity, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("itemId", itemId);
        content.put("quantity", quantity);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SELL_ITEM);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO sellCollectionRequest(String itemId, int quantity, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("itemId", itemId);
        content.put("quantity", quantity);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.SELL_COLLECTION);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO useItemRequest(String itemId, String dogId, long requestId) {
        return useItemRequest(itemId, dogId, 1, requestId);
    }

    private static PetRequestDTO useItemRequestWithoutQuantity(String itemId, String dogId, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("itemId", itemId);
        content.put("dogId", dogId);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.USE_ITEM);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static PetRequestDTO useItemRequest(String itemId, String dogId, int quantity, long requestId) {
        return useItemRequest(itemId, dogId, Integer.valueOf(quantity), requestId);
    }

    private static PetRequestDTO useItemRequest(String itemId, String dogId, Integer quantity, long requestId) {
        Map<String, Object> content = new HashMap<>();
        content.put("itemId", itemId);
        content.put("dogId", dogId);
        content.put("quantity", quantity);
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.USE_ITEM);
        request.setRequestId(requestId);
        request.setContent(content);
        return request;
    }

    private static String pastDateInCurrentMonthOrSkip() {
        LocalDate today = LocalDate.now();
        Assume.assumeTrue("每月 1 号没有当前月份内且早于今天的日期可用于补签正向用例",
                today.getDayOfMonth() > 1);
        return today.minusDays(1).toString();
    }

    private static void setMakeupCards(long accountId, int makeupCards) {
        ensureAssetsRow(accountId);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getConnection().createStatement().executeUpdate(
                    "UPDATE pet_assets SET makeup_cards = " + makeupCards + " WHERE account_id = " + accountId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setCompanionDogId(long accountId, String dogId) {
        ensureAssetsRow(accountId);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET companion_dog_id = ? WHERE account_id = ?")) {
            statement.setString(1, dogId);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setAssets(long accountId, int bones, int dogSlots) {
        ensureAssetsRow(accountId);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET bones = ?, dog_slots = ? WHERE account_id = ?")) {
            statement.setInt(1, bones);
            statement.setInt(2, dogSlots);
            statement.setLong(3, accountId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setFood(long accountId, int food) {
        ensureAssetsRow(accountId);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET food = ? WHERE account_id = ?")) {
            statement.setInt(1, food);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setEnergyLimit(long accountId, int energyLimit) {
        ensureAssetsRow(accountId);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE pet_assets SET energy_limit = ? WHERE account_id = ?")) {
            statement.setInt(1, energyLimit);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDogEnergy(long accountId, String dogId, int energy, String energyDate) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET energy = ?, energy_date = ? WHERE owner_id = ? AND id = ?")) {
            statement.setInt(1, energy);
            statement.setString(2, energyDate);
            statement.setLong(3, accountId);
            statement.setString(4, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDogStatus(long accountId, String dogId, String status) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET status = ?, updated_at = ? WHERE owner_id = ? AND id = ?")) {
            statement.setString(1, status);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, accountId);
            statement.setString(4, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDogStage(long accountId, String dogId, String stage) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET stage = ?, updated_at = ? WHERE owner_id = ? AND id = ?")) {
            statement.setString(1, stage);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, accountId);
            statement.setString(4, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDogGrowthProgress(long accountId, String dogId, String stage,
                                             int speed, int stamina, int burst, int wisdom, int bond,
                                             int raceCount, int raceFirstCount) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET stage = ?, speed = ?, stamina = ?, burst = ?, wisdom = ?, bond = ?, " +
                             "race_count = ?, race_first_count = ?, updated_at = ? " +
                             "WHERE owner_id = ? AND id = ?")) {
            statement.setString(1, stage);
            statement.setInt(2, speed);
            statement.setInt(3, stamina);
            statement.setInt(4, burst);
            statement.setInt(5, wisdom);
            statement.setInt(6, bond);
            statement.setInt(7, raceCount);
            statement.setInt(8, raceFirstCount);
            statement.setLong(9, System.currentTimeMillis());
            statement.setLong(10, accountId);
            statement.setString(11, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertDogRaceProgress(long accountId, String dogId, int raceCount, int raceFirstCount) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT race_count, race_first_count FROM dogs WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, dogId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                Assert.assertTrue(rs.next());
                Assert.assertEquals(raceCount, rs.getInt(1));
                Assert.assertEquals(raceFirstCount, rs.getInt(2));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setExploreEnded(long accountId, String dogId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET explore_ends_at = ? WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, System.currentTimeMillis() - 1000L);
            statement.setLong(2, accountId);
            statement.setString(3, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clearExploreEndsAt(long accountId, String dogId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET explore_ends_at = NULL WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clearExploreDurationHours(long accountId, String dogId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET explore_duration_hours = NULL WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setLegacyExploreWindow(long accountId, String dogId, long updatedAt, long exploreEndsAt) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE dogs SET explore_duration_hours = NULL, updated_at = ?, explore_ends_at = ? " +
                             "WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, updatedAt);
            statement.setLong(2, exploreEndsAt);
            statement.setLong(3, accountId);
            statement.setString(4, dogId);
            Assert.assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertBackHillChestSettledAfterExpress(User user,
                                                               String dogId,
                                                               long expressRequestId,
                                                               long openRequestId) {
        insertPetItem(user.getAccountId(), "item_express", 1);

        new PetActionHandler().process(user, useItemRequest("item_express", dogId, expressRequestId));
        Assert.assertTrue(readPetBody(user).isSuccess());
        Assert.assertEquals(1, countItem(user.getAccountId(), BACK_HILL_CHEST_ITEM_ID));

        IntSupplier originalRollSupplier = setExploreRollSupplier(() -> 99);
        try {
            new PetActionHandler().process(user, useItemRequest(BACK_HILL_CHEST_ITEM_ID, null, openRequestId));

            PetResponseDTO openBody = readPetBody(user);
            Assert.assertTrue(openBody.isSuccess());
            JSONObject result = JSONUtil.parseObj(openBody.getContent());
            PetProfileDTO openProfile = result.getBean("profile", PetProfileDTO.class);
            JSONArray rewards = result.getJSONArray("rewards");
            Assert.assertEquals(2, rewards.size());
            Assert.assertEquals(325, openProfile.getAssets().getBones());
        } finally {
            setExploreRollSupplier(originalRollSupplier);
        }
    }

    private static String findDogEnergyDate(String dogId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT energy_date FROM dogs WHERE id = ?")) {
            statement.setString(1, dogId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                Assert.assertTrue(rs.next());
                return rs.getString(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void ensureAssetsRow(long accountId) {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT OR IGNORE INTO pet_assets " +
                             "(account_id, bones, food, makeup_cards, dog_slots, energy_limit, companion_dog_id, created_at, updated_at) " +
                             "VALUES (?, 300, 6, 0, 1, 10, NULL, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertDailyCounter(long accountId, String counterDate, String counter, int value) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_daily_counters " +
                             "(account_id, counter_date, counter, value, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, counterDate);
            statement.setString(3, counter);
            statement.setInt(4, value);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertPetItem(long accountId, String itemId, int count) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
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

    private static void insertPetCollection(long accountId, String itemId, int count, boolean discovered) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                              "VALUES (?, ?, ?, ?, ?) " +
                              "ON CONFLICT(account_id, item_id) DO UPDATE SET " +
                              "count = excluded.count, discovered = excluded.discovered, updated_at = excluded.updated_at")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setInt(4, discovered ? 1 : 0);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countItem(long accountId, String itemId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
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

    private static int findInventoryCount(PetProfileDTO profile, String itemId) {
        for (PetInventoryItemDTO item : profile.getItems()) {
            if (itemId.equals(item.getItemId())) {
                return item.getCount();
            }
        }
        return 0;
    }

    private static PetCollectionItemDTO findCollection(PetProfileDTO profile, String itemId) {
        for (PetCollectionItemDTO item : profile.getCollections()) {
            if (itemId.equals(item.getItemId())) {
                return item;
            }
        }
        return null;
    }

    private static void setMonthlyCounter(long accountId, String counter, int value) {
        insertDailyCounter(accountId, YearMonth.now().toString(), counter, value);
    }

    private static void setDailyCounter(long accountId, String counter, int value) {
        insertDailyCounter(accountId, LocalDate.now().toString(), counter, value);
    }

    private static int countMonthlyCounter(long accountId, String counter) {
        return countCounter(accountId, YearMonth.now().toString(), counter);
    }

    private static int countDailyCounter(long accountId, String counter) {
        return countCounter(accountId, LocalDate.now().toString(), counter);
    }

    private static int countCounter(long accountId, String counterDate, String counter) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(value), 0) FROM pet_daily_counters " +
                             "WHERE account_id = ? AND counter_date = ? AND counter = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, counterDate);
            statement.setString(3, counter);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countCheckins(long accountId, String checkinDate) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM pet_checkins WHERE account_id = ? AND checkin_date = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, checkinDate);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countAllCheckins(long accountId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM pet_checkins WHERE account_id = ?")) {
            statement.setLong(1, accountId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean petAssetsExists(long accountId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM pet_assets WHERE account_id = ?")) {
            statement.setLong(1, accountId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void seedSixHistoricalCheckins(long accountId) {
        LocalDate startDate = LocalDate.now().minusDays(60);
        for (int i = 0; i < 6; i++) {
            insertCheckin(accountId, startDate.plusDays(i).toString(), i + 1);
        }
    }

    private static void insertCheckin(long accountId, String checkinDate, int cycleDay) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_checkins (account_id, checkin_date, cycle_day, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, checkinDate);
            statement.setInt(3, cycleDay);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static IntSupplier setExploreRollSupplier(IntSupplier supplier) {
        try {
            Field field = PetProfileService.class.getDeclaredField("exploreRollSupplier");
            field.setAccessible(true);
            IntSupplier original = (IntSupplier) field.get(null);
            field.set(null, supplier);
            return original;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static IntSupplier setExploreEasterEventSupplier(IntSupplier supplier) {
        try {
            Field field = PetProfileService.class.getDeclaredField("exploreEasterEventSupplier");
            field.setAccessible(true);
            IntSupplier original = (IntSupplier) field.get(null);
            field.set(null, supplier);
            return original;
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
