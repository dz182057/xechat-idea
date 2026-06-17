package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetAssetsDTO;
import cn.xeblog.commons.entity.pet.PetCheckinStatusDTO;
import cn.xeblog.commons.entity.pet.PetCollectionItemDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenResultDTO;
import cn.xeblog.commons.entity.pet.PetExploreRewardDTO;
import cn.xeblog.commons.entity.pet.PetExploreStartDTO;
import cn.xeblog.commons.entity.pet.PetExploreStatusDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetInventoryItemDTO;
import cn.xeblog.commons.entity.pet.PetMakeupCheckinDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetRenameDTO;
import cn.xeblog.commons.entity.pet.PetSellItemDTO;
import cn.xeblog.commons.entity.pet.PetSetCompanionDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetUseItemDTO;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * 狗狗宇宙个人资料服务。
 */
public final class PetProfileService {

    private static final int DEFAULT_BONES = 300;
    private static final int DEFAULT_FOOD = 6;
    private static final int DEFAULT_MAKEUP_CARDS = 0;
    private static final int DEFAULT_DOG_SLOTS = 1;
    private static final int MAX_DOG_SLOTS = 2;
    private static final int SECOND_DOG_SLOT_PRICE = 2000;
    private static final int DEFAULT_ENERGY_LIMIT = 10;
    private static final int DAILY_FEED_LIMIT = 5;
    private static final String DOG_STAGE_PUPPY = "puppy";
    private static final String DOG_STAGE_ADULT = "adult";
    private static final String DOG_STAGE_CHAMPION = "champion";
    private static final int DOG_ADULT_TOTAL_STATS_THRESHOLD = 150;
    private static final int DOG_ADULT_RACE_COUNT_THRESHOLD = 3;
    private static final int DOG_CHAMPION_TOTAL_STATS_THRESHOLD = 300;
    private static final int DOG_CHAMPION_RACE_FIRST_COUNT_THRESHOLD = 1;
    private static final int DOG_RACE_DOG_COUNT = 5;
    private static final String SHOP_ITEM_FOOD = "food";
    private static final String SHOP_ITEM_MAKEUP_CARD = "makeup_card";
    private static final String SHOP_ITEM_LUCKY_BAG = "lucky_bag";
    private static final int SHOP_FOOD_PRICE = 30;
    private static final int SHOP_MAKEUP_CARD_PRICE = 150;
    private static final int SHOP_NORMAL_ITEM_PRICE = 80;
    private static final int SHOP_LUCKY_BAG_PRICE = 250;
    private static final int SELL_NORMAL_ITEM_PRICE = 20;
    private static final int SELL_RARE_ITEM_PRICE = 80;
    private static final int SELL_EPIC_ITEM_PRICE = 200;
    private static final int MAX_FOOD = 99;
    private static final int MAX_MAKEUP_CARDS = 3;
    private static final int MAX_ITEM_COUNT = 9;
    private static final int MONTHLY_MAKEUP_CARD_BUY_LIMIT = 2;
    private static final int DAILY_NORMAL_ITEM_BUY_LIMIT = 3;
    private static final int DAILY_LUCKY_BAG_BUY_LIMIT = 2;
    private static final int SEVENTH_DAY_CHECKIN_BONES = 100;
    private static final int SEVENTH_DAY_CHECKIN_NORMAL_ITEM_COUNT = 2;
    private static final int CHECKIN_ITEM_OVERFLOW_BONES = 10;
    private static final String EXPLORE_LOCATION_BACK_HILL = "back_hill";
    private static final int EXPLORE_ONE_HOUR = 1;
    private static final int EXPLORE_FOUR_HOURS = 4;
    private static final int EXPLORE_EIGHT_HOURS = 8;
    private static final int EXPLORE_TWELVE_HOURS = 12;
    private static final int EXPLORE_ONE_HOUR_ENERGY_COST = 2;
    private static final int EXPLORE_FOUR_HOURS_ENERGY_COST = 3;
    private static final int EXPLORE_EIGHT_HOURS_ENERGY_COST = 4;
    private static final int EXPLORE_TWELVE_HOURS_ENERGY_COST = 5;
    private static final int EXPLORE_ONE_HOUR_BASE_BONES = 10;
    private static final int EXPLORE_FOUR_HOURS_BASE_BONES = 25;
    private static final int EXPLORE_EIGHT_HOURS_BASE_BONES = 45;
    private static final int EXPLORE_TWELVE_HOURS_BASE_BONES = 60;
    private static final int EXPLORE_ONE_HOUR_ROLL_COUNT = 1;
    private static final int EXPLORE_FOUR_HOURS_ROLL_COUNT = 2;
    private static final int EXPLORE_EIGHT_HOURS_ROLL_COUNT = 3;
    private static final int EXPLORE_TWELVE_HOURS_ROLL_COUNT = 4;
    private static final String INVALID_EXPLORE_RESET_ERROR = "探险数据异常，已重置，请重新开始探险";
    private static final int EXPLORE_ROLL_BONES = 15;
    private static final int EXPLORE_ITEM_OVERFLOW_BONES = 10;
    private static final int HUSKY_TREASURE_MAP_FRAGMENT_LIMIT = 3;
    private static final int DAILY_EXPLORE_START_LIMIT = 3;
    private static final int DAILY_EXPLORE_ITEM_GAIN_LIMIT = 5;
    private static final String TREASURE_MAP_FRAGMENT_COLLECTION_ID = "treasure_map_fragment";
    private static final String ITEM_FEAST = "item_feast";
    private static final String ITEM_EXPRESS = "item_express";
    private static final String DAILY_COUNTER_FEED_FOOD = "feed_food";
    private static final String DAILY_COUNTER_USE_ITEM_FEAST = "use_item_feast";
    private static final String DAILY_COUNTER_USE_ITEM_EXPRESS = "use_item_express";
    private static final String DAILY_COUNTER_SHOP_NORMAL_ITEM_BUY = "shop_normal_item_buy";
    private static final String DAILY_COUNTER_SHOP_LUCKY_BAG_BUY = "shop_lucky_bag_buy";
    private static final String DAILY_COUNTER_EXPLORE_START = "explore_start";
    private static final String DAILY_COUNTER_EXPLORE_ITEM_GAIN = "explore_item_gain";
    private static final String MONTHLY_COUNTER_SHOP_MAKEUP_CARD_BUY = "shop_makeup_card_buy";
    private static final List<String> LUCKY_BAG_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_mine_mark",
            "item_mine_area",
            "item_hint",
            "item_custom_word",
            "item_eraser",
            "item_palette",
            "item_time",
            "item_rematch",
            "item_first_move",
            "item_sonar",
            "item_pinyin_sniff"
    ));
    private static final List<String> LUCKY_BAG_RARE_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_shield",
            "item_metal_detector",
            "item_regret",
            "item_clue",
            "item_gold_bone",
            "item_reroll",
            "item_extra_round",
            "item_feast"
    ));
    private static final List<String> LUCKY_BAG_EPIC_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_telepathy",
            "item_express",
            "item_lucky_day"
    ));
    private static final List<String> BACK_HILL_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_hint",
            "item_time",
            "item_rematch"
    ));
    private static final List<String> BACK_HILL_RARE_ITEM_IDS = Collections.unmodifiableList(Collections.singletonList(
            ITEM_FEAST
    ));
    private static final List<String> BACK_HILL_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "back_hill_ball",
            "back_hill_branch",
            "back_hill_leaf",
            "back_hill_stone",
            "back_hill_mushroom",
            "back_hill_feather"
    ));
    private static final List<String> LUCKY_BAG_ITEM_IDS = createLuckyBagItemIds();
    private static final Set<String> SHOP_NORMAL_ITEM_IDS = Collections.unmodifiableSet(
            new HashSet<>(LUCKY_BAG_NORMAL_ITEM_IDS));
    private static final Map<String, Integer> SELL_ITEM_PRICES = createSellItemPrices();
    private static final Map<Long, Object> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
    private static IntSupplier exploreRollSupplier = () -> ThreadLocalRandom.current().nextInt(100);

    private PetProfileService() {
    }

    public static PetProfileDTO profile(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = findAssetsOrDefault(session, accountId);
            LocalDate today = LocalDate.now();
            String todayText = today.toString();
            long now = System.currentTimeMillis();
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            boolean dogEnergyRefreshed = refreshExpiredDogEnergy(dogMapper, accountId,
                    assets.getEnergyLimit(), todayText, now);
            List<PetDogRecord> rows = dogMapper.listByOwner(accountId);
            boolean dogStageChanged = updateDogGrowthStages(dogMapper, accountId, rows, now);
            List<PetItemRecord> itemRows = session.getMapper(PetItemMapper.class).listPositiveByAccountId(accountId);
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            List<PetCollectionRecord> collectionRows = collectionMapper.listByAccountId(accountId);
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
            PetDailyCounterMapper dailyCounterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetCheckinRecord todayCheckin = checkinMapper.findByAccountIdAndDate(accountId, todayText);
            List<String> checkedDatesInMonth = checkinMapper.listDatesByAccountIdAndMonthPrefix(
                    accountId, todayText.substring(0, 7));
            int cycleDay = todayCheckin == null ? checkinMapper.countByAccountId(accountId) % 7 + 1
                    : todayCheckin.getCycleDay();
            PetProfileDTO profile = new PetProfileDTO();
            profile.setAccountId(accountId);
            profile.setAssets(toDTO(assets));
            List<PetDogDTO> dogs = new ArrayList<>(rows.size());
            for (PetDogRecord row : rows) {
                dogs.add(toDTO(row));
            }
            profile.setDogs(dogs);
            List<PetInventoryItemDTO> items = new ArrayList<>(itemRows.size());
            for (PetItemRecord row : itemRows) {
                items.add(toDTO(row));
            }
            profile.setItems(items);
            List<PetCollectionItemDTO> collections = new ArrayList<>(collectionRows.size());
            for (PetCollectionRecord row : collectionRows) {
                collections.add(toDTO(row));
            }
            profile.setCollections(collections);
            profile.setCompanionDogId(resolveCompanionDogId(assets.getCompanionDogId(), dogs));
            profile.setCheckinStatus(new PetCheckinStatusDTO(todayText, todayCheckin != null, cycleDay,
                    checkedDatesInMonth));
            profile.setExploreStatus(new PetExploreStatusDTO(
                    DAILY_EXPLORE_START_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_START),
                    DAILY_EXPLORE_ITEM_GAIN_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_ITEM_GAIN),
                    Math.min(HUSKY_TREASURE_MAP_FRAGMENT_LIMIT,
                            findCollectionCount(collectionMapper, accountId, TREASURE_MAP_FRAGMENT_COLLECTION_ID)),
                    findCollectionCount(collectionMapper, accountId, TREASURE_MAP_FRAGMENT_COLLECTION_ID)
                            >= HUSKY_TREASURE_MAP_FRAGMENT_LIMIT));
            if (dogEnergyRefreshed || dogStageChanged) {
                session.commit();
            }
            return profile;
        }
    }

    public static PetProfileDTO adopt(long accountId, PetAdoptDTO request) {
        synchronized (accountLock(accountId)) {
            return adoptLocked(accountId, request);
        }
    }

    private static PetProfileDTO adoptLocked(long accountId, PetAdoptDTO request) {
        String breed = request == null ? null : StrUtil.trim(request.getBreed());
        String name = request == null ? null : StrUtil.trim(request.getName());
        BreedStats stats = BreedStats.of(breed);
        if (stats == null || stats.hidden) {
            throw new IllegalArgumentException("该品种暂不可领养");
        }
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("狗狗名字不能为空");
        }
        if (name.length() > 6) {
            throw new IllegalArgumentException("狗狗名字不能超过 6 个字符");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper mapper = session.getMapper(PetDogMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if (mapper.countByOwner(accountId) >= assets.getDogSlots()) {
                throw new IllegalArgumentException("当前狗位已满");
            }

            mapper.insert(PetDogRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .ownerId(accountId)
                    .name(name)
                    .breed(breed)
                    .stage("puppy")
                    .speed(stats.speed)
                    .stamina(stats.stamina)
                    .burst(stats.burst)
                    .wisdom(stats.wisdom)
                    .bond(stats.bond)
                    .energy(10)
                    .energyDate(LocalDate.now().toString())
                    .status("idle")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            session.commit();
        }

        return profile(accountId);
    }

    public static PetProfileDTO rename(long accountId, PetRenameDTO request) {
        synchronized (accountLock(accountId)) {
            return renameLocked(accountId, request);
        }
    }

    private static PetProfileDTO renameLocked(long accountId, PetRenameDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        String name = request == null ? null : StrUtil.trim(request.getName());
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("狗狗名字不能为空");
        }
        if (name.length() > 6) {
            throw new IllegalArgumentException("狗狗名字不能超过 6 个字符");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper mapper = session.getMapper(PetDogMapper.class);
            if (StrUtil.isBlank(dogId) || mapper.findByIdAndOwner(dogId, accountId) == null) {
                throw new IllegalArgumentException("只能修改自己的狗狗");
            }
            mapper.updateName(dogId, accountId, name, now);
            session.commit();
        }

        return profile(accountId);
    }

    public static PetProfileDTO feed(long accountId, PetFeedDTO request) {
        synchronized (accountLock(accountId)) {
            return feedLocked(accountId, request);
        }
    }

    private static PetProfileDTO feedLocked(long accountId, PetFeedDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            refreshExpiredDogEnergy(dogMapper, accountId, assets.getEnergyLimit(), today, now);
            PetDogRecord dog = StrUtil.isBlank(dogId) ? null : dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能喂自己的狗狗");
            }

            if (session.getMapper(PetDailyCounterMapper.class).incrementIfUnderLimit(accountId,
                    today, DAILY_COUNTER_FEED_FOOD, DAILY_FEED_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日喂食次数已达上限");
            }
            if (session.getMapper(PetAssetsMapper.class).decrementFoodIfEnough(accountId, now) <= 0) {
                throw new IllegalArgumentException("狗粮不足");
            }

            int bond = Math.min(100, dog.getBond() + 10);
            int energy = Math.min(assets.getEnergyLimit(), dog.getEnergy() + 1);
            dogMapper.updateCareStats(dog.getId(), accountId, bond, energy, now);
            session.commit();
        }

        return profile(accountId);
    }

    public static PetProfileDTO checkin(long accountId) {
        synchronized (accountLock(accountId)) {
            try {
                return checkinLocked(accountId);
            } catch (PersistenceException e) {
                if (isUniqueConstraint(e)) {
                    throw new IllegalArgumentException("今天已经签到过了", e);
                }
                throw e;
            }
        }
    }

    public static PetProfileDTO makeupCheckin(long accountId, PetMakeupCheckinDTO request) {
        synchronized (accountLock(accountId)) {
            try {
                return makeupCheckinLocked(accountId, request);
            } catch (PersistenceException e) {
                if (isUniqueConstraint(e)) {
                    throw new IllegalArgumentException("该日期已经签到过了", e);
                }
                throw e;
            }
        }
    }

    public static PetProfileDTO buySlot(long accountId) {
        synchronized (accountLock(accountId)) {
            return buySlotLocked(accountId);
        }
    }

    public static PetProfileDTO shopBuy(long accountId, PetShopBuyDTO request) {
        synchronized (accountLock(accountId)) {
            return shopBuyLocked(accountId, request);
        }
    }

    public static PetProfileDTO sellItem(long accountId, PetSellItemDTO request) {
        synchronized (accountLock(accountId)) {
            return sellItemLocked(accountId, request);
        }
    }

    public static PetProfileDTO useItem(long accountId, PetUseItemDTO request) {
        synchronized (accountLock(accountId)) {
            return useItemLocked(accountId, request);
        }
    }

    public static PetProfileDTO exploreStart(long accountId, PetExploreStartDTO request) {
        synchronized (accountLock(accountId)) {
            return exploreStartLocked(accountId, request);
        }
    }

    public static PetExploreOpenResultDTO exploreOpen(long accountId, PetExploreOpenDTO request) {
        synchronized (accountLock(accountId)) {
            return exploreOpenLocked(accountId, request);
        }
    }

    public static PetProfileDTO recordRaceResult(long accountId, PetRaceResultDTO request) {
        synchronized (accountLock(accountId)) {
            return recordRaceResultLocked(accountId, request);
        }
    }

    public static PetProfileDTO setCompanion(long accountId, PetSetCompanionDTO request) {
        synchronized (accountLock(accountId)) {
            return setCompanionLocked(accountId, request);
        }
    }

    private static PetProfileDTO exploreStartLocked(long accountId, PetExploreStartDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        String location = request == null ? null : StrUtil.trim(request.getLocation());
        int durationHours = request == null ? 0 : request.getDurationHours();
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }
        if (!EXPLORE_LOCATION_BACK_HILL.equals(location)) {
            throw new IllegalArgumentException("暂不支持该探险地点");
        }
        if (!isSupportedExploreDuration(durationHours)) {
            throw new IllegalArgumentException("暂不支持该探险时长");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        int energyCost = exploreEnergyCost(durationHours);
        long exploreEndsAt = now + durationHours * 60L * 60L * 1000L;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            refreshExpiredDogEnergy(dogMapper, accountId, assets.getEnergyLimit(), today, now);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能派遣自己的狗狗探险");
            }
            updateDogGrowthStage(dogMapper, accountId, dog, now);
            if (!"idle".equals(dog.getStatus())) {
                throw new IllegalArgumentException("只有空闲狗狗可以去探险");
            }
            if (dog.getEnergy() < energyCost) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            ensureExploreStageUnlocked(dog, durationHours);
            if (session.getMapper(PetDailyCounterMapper.class).incrementIfUnderLimit(accountId,
                    today, DAILY_COUNTER_EXPLORE_START, DAILY_EXPLORE_START_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日探险派遣次数已达上限");
            }
            if (dogMapper.startExplore(dogId, accountId, energyCost, location, exploreEndsAt,
                    durationHours, now) <= 0) {
                throw new IllegalArgumentException("探险派遣失败，请刷新后重试");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetExploreOpenResultDTO exploreOpenLocked(long accountId, PetExploreOpenDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        List<PetExploreRewardDTO> rewards = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能开启自己的狗狗探险宝箱");
            }
            if (!"exploring".equals(dog.getStatus())) {
                throw new IllegalArgumentException("狗狗当前没有正在等待开箱的探险");
            }
            if (dog.getExploreEndsAt() == null) {
                resetInvalidExploreAndThrow(session, dogMapper, dogId, accountId, now);
            }
            if (dog.getExploreEndsAt() > now) {
                throw new IllegalArgumentException("探险还没有结束，请稍后再来开箱");
            }

            int durationHours = inferExploreDurationHours(dog);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            addExploreBones(assetsMapper, accountId, exploreBaseBones(durationHours), rewards, now);
            applyExploreRolls(session, accountId, durationHours, rewards, today, now);
            if (dogMapper.openExplore(dogId, accountId, now) <= 0) {
                throw new IllegalArgumentException("探险开箱失败，请刷新后重试");
            }
            session.commit();
        }

        return new PetExploreOpenResultDTO(profile(accountId), rewards);
    }

    private static PetProfileDTO useItemLocked(long accountId, PetUseItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        Integer quantity = request == null ? null : request.getQuantity();
        if (StrUtil.isBlank(itemId)) {
            throw new IllegalArgumentException("道具不能为空");
        }
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗不能为空");
        }
        if (quantity == null || quantity != 1) {
            throw new IllegalArgumentException("道具使用数量必须为 1");
        }
        if (ITEM_FEAST.equals(itemId)) {
            return useFeastItem(accountId, dogId);
        }
        if (ITEM_EXPRESS.equals(itemId)) {
            return useExpressItem(accountId, dogId);
        }
        throw new IllegalArgumentException("暂不支持该道具");
    }

    private static PetProfileDTO useFeastItem(long accountId, String dogId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            refreshExpiredDogEnergy(dogMapper, accountId, assets.getEnergyLimit(), today, now);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能给自己的狗狗使用道具");
            }
            if (dog.getEnergy() >= assets.getEnergyLimit()) {
                throw new IllegalArgumentException("狗狗活力已满");
            }

            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_USE_ITEM_FEAST, 1, now) <= 0) {
                throw new IllegalArgumentException("今日美食大餐已使用");
            }
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (itemMapper.decrementItemIfEnough(accountId, ITEM_FEAST, 1, now) <= 0) {
                throw new IllegalArgumentException("道具数量不足");
            }
            dogMapper.updateCareStats(dog.getId(), accountId, dog.getBond(), assets.getEnergyLimit(), now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO useExpressItem(long accountId, String dogId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            refreshExpiredDogEnergy(dogMapper, accountId, assets.getEnergyLimit(), today, now);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能给自己的狗狗使用加急快递");
            }
            if (!"exploring".equals(dog.getStatus())) {
                throw new IllegalArgumentException("只有探险中的狗狗可以使用加急快递");
            }
            if (dog.getExploreEndsAt() == null) {
                resetInvalidExploreAndThrow(session, dogMapper, dogId, accountId, now);
            }
            if (dog.getExploreEndsAt() <= now) {
                throw new IllegalArgumentException("探险已经完成，请直接开箱");
            }

            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_USE_ITEM_EXPRESS, 1, now) <= 0) {
                throw new IllegalArgumentException("今日加急快递已使用");
            }
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (itemMapper.decrementItemIfEnough(accountId, ITEM_EXPRESS, 1, now) <= 0) {
                throw new IllegalArgumentException("道具数量不足");
            }
            if (dogMapper.finishExploreNow(dogId, accountId, now, inferExploreDurationHours(dog), now) <= 0) {
                throw new IllegalArgumentException("加急快递使用失败，请刷新后重试");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO recordRaceResultLocked(long accountId, PetRaceResultDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        int rank = request == null ? 0 : request.getRank();
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }
        if (rank < 1 || rank > DOG_RACE_DOG_COUNT) {
            throw new IllegalArgumentException("狗狗赛跑结果无效");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int firstPlaceIncrement = rank == 1 ? 1 : 0;
            if (dogMapper.recordRaceResult(dogId, accountId, firstPlaceIncrement, now) <= 0) {
                throw new IllegalArgumentException("只能结算自己的狗狗赛跑结果");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO setCompanionLocked(long accountId, PetSetCompanionDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            if (StrUtil.isBlank(dogId) || dogMapper.findByIdAndOwner(dogId, accountId) == null) {
                throw new IllegalArgumentException("只能设置自己的狗狗");
            }
            ensureAssets(session, accountId);
            session.getMapper(PetAssetsMapper.class).updateCompanionDogId(accountId, dogId, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO shopBuyLocked(long accountId, PetShopBuyDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        int quantity = request == null ? 0 : request.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须为正整数");
        }
        if (SHOP_ITEM_FOOD.equals(itemId)) {
            return buyFood(accountId, quantity);
        }
        if (SHOP_ITEM_MAKEUP_CARD.equals(itemId)) {
            return buyMakeupCard(accountId, quantity);
        }
        if (SHOP_ITEM_LUCKY_BAG.equals(itemId)) {
            return buyLuckyBag(accountId, quantity);
        }
        if (SHOP_NORMAL_ITEM_IDS.contains(itemId)) {
            return buyNormalItem(accountId, itemId, quantity);
        }
        throw new IllegalArgumentException("暂不支持该商店商品");
    }

    private static PetProfileDTO sellItemLocked(long accountId, PetSellItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        int quantity = request == null ? 0 : request.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("出售数量必须为正整数");
        }
        Integer unitPrice = SELL_ITEM_PRICES.get(itemId);
        if (unitPrice == null) {
            throw new IllegalArgumentException("暂不支持出售该物品");
        }

        long now = System.currentTimeMillis();
        long bonesValue = (long) unitPrice * quantity;
        if (bonesValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("出售数量过大");
        }
        int bones = (int) bonesValue;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (itemMapper.decrementItemIfEnough(accountId, itemId, quantity, now) <= 0) {
                throw new IllegalArgumentException("道具数量不足");
            }
            if (session.getMapper(PetAssetsMapper.class).addBones(accountId, bones, now) <= 0) {
                throw new IllegalArgumentException("资源变更失败");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyFood(long accountId, int quantity) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if ((long) assets.getFood() + quantity > MAX_FOOD) {
                throw new IllegalArgumentException("狗粮持有数量不能超过 99");
            }

            int price = SHOP_FOOD_PRICE * quantity;
            if (assets.getBones() < price) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (mapper.buyFoodIfAffordableAndUnderLimit(accountId, quantity, price, MAX_FOOD, now) <= 0) {
                PetAssetsRecord latest = mapper.findByAccountId(accountId);
                if (latest != null && (long) latest.getFood() + quantity > MAX_FOOD) {
                    throw new IllegalArgumentException("狗粮持有数量不能超过 99");
                }
                throw new IllegalArgumentException("骨头币不足");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyMakeupCard(long accountId, int quantity) {
        if (quantity > MONTHLY_MAKEUP_CARD_BUY_LIMIT) {
            throw new IllegalArgumentException("本月补签卡购买次数已达上限");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            ensureAssets(session, accountId);
            if (counterMapper.incrementByIfUnderLimit(accountId, YearMonth.now().toString(),
                    MONTHLY_COUNTER_SHOP_MAKEUP_CARD_BUY, quantity, MONTHLY_MAKEUP_CARD_BUY_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("本月补签卡购买次数已达上限");
            }

            int price = SHOP_MAKEUP_CARD_PRICE * quantity;
            if (assetsMapper.buyMakeupCardsIfAffordableAndUnderLimit(accountId, quantity, price,
                    MAX_MAKEUP_CARDS, now) <= 0) {
                PetAssetsRecord latest = assetsMapper.findByAccountId(accountId);
                if (latest != null && (long) latest.getMakeupCards() + quantity > MAX_MAKEUP_CARDS) {
                    throw new IllegalArgumentException("补签卡持有数量不能超过 3");
                }
                throw new IllegalArgumentException("骨头币不足");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyNormalItem(long accountId, String itemId, int quantity) {
        if (quantity > DAILY_NORMAL_ITEM_BUY_LIMIT) {
            throw new IllegalArgumentException("今日普通道具购买次数已达上限");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetItemRecord item = itemMapper.findByAccountIdAndItemId(accountId, itemId);
            int currentCount = item == null ? 0 : item.getCount();
            if ((long) currentCount + quantity > MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("道具卡持有数量不能超过 9");
            }

            int price = SHOP_NORMAL_ITEM_PRICE * quantity;
            if (assets.getBones() < price) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (counterMapper.incrementByIfUnderLimit(accountId, today, DAILY_COUNTER_SHOP_NORMAL_ITEM_BUY,
                    quantity, DAILY_NORMAL_ITEM_BUY_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日普通道具购买次数已达上限");
            }
            if (assetsMapper.decrementBonesIfEnough(accountId, price, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (itemMapper.addItemIfUnderLimit(accountId, itemId, quantity, MAX_ITEM_COUNT, now) <= 0) {
                throw new IllegalArgumentException("道具卡持有数量不能超过 9");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyLuckyBag(long accountId, int quantity) {
        if (quantity > DAILY_LUCKY_BAG_BUY_LIMIT) {
            throw new IllegalArgumentException("今日狗狗福袋购买次数已达上限");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        int price = SHOP_LUCKY_BAG_PRICE * quantity;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if (assets.getBones() < price) {
                throw new IllegalArgumentException("骨头币不足");
            }

            Map<String, Integer> itemCounts = luckyBagItemCounts(itemMapper, accountId);
            List<String> rewards = new ArrayList<>(quantity);
            for (int i = 0; i < quantity; i++) {
                String rewardItemId = rollLuckyBagItem(itemCounts);
                if (rewardItemId == null) {
                    throw new IllegalArgumentException("道具背包已满");
                }
                rewards.add(rewardItemId);
                itemCounts.put(rewardItemId, itemCounts.getOrDefault(rewardItemId, 0) + 1);
            }

            if (counterMapper.incrementByIfUnderLimit(accountId, today, DAILY_COUNTER_SHOP_LUCKY_BAG_BUY,
                    quantity, DAILY_LUCKY_BAG_BUY_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日狗狗福袋购买次数已达上限");
            }
            if (assetsMapper.decrementBonesIfEnough(accountId, price, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            for (String rewardItemId : rewards) {
                if (itemMapper.addItemIfUnderLimit(accountId, rewardItemId, 1, MAX_ITEM_COUNT, now) <= 0) {
                    throw new IllegalArgumentException("道具背包已满");
                }
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static int exploreEnergyCost(int durationHours) {
        switch (durationHours) {
            case EXPLORE_TWELVE_HOURS:
                return EXPLORE_TWELVE_HOURS_ENERGY_COST;
            case EXPLORE_EIGHT_HOURS:
                return EXPLORE_EIGHT_HOURS_ENERGY_COST;
            case EXPLORE_FOUR_HOURS:
                return EXPLORE_FOUR_HOURS_ENERGY_COST;
            default:
                return EXPLORE_ONE_HOUR_ENERGY_COST;
        }
    }

    private static int exploreBaseBones(int durationHours) {
        switch (durationHours) {
            case EXPLORE_TWELVE_HOURS:
                return EXPLORE_TWELVE_HOURS_BASE_BONES;
            case EXPLORE_EIGHT_HOURS:
                return EXPLORE_EIGHT_HOURS_BASE_BONES;
            case EXPLORE_FOUR_HOURS:
                return EXPLORE_FOUR_HOURS_BASE_BONES;
            default:
                return EXPLORE_ONE_HOUR_BASE_BONES;
        }
    }

    private static int exploreRollCount(int durationHours) {
        switch (durationHours) {
            case EXPLORE_TWELVE_HOURS:
                return EXPLORE_TWELVE_HOURS_ROLL_COUNT;
            case EXPLORE_EIGHT_HOURS:
                return EXPLORE_EIGHT_HOURS_ROLL_COUNT;
            case EXPLORE_FOUR_HOURS:
                return EXPLORE_FOUR_HOURS_ROLL_COUNT;
            default:
                return EXPLORE_ONE_HOUR_ROLL_COUNT;
        }
    }

    private static int inferExploreDurationHours(PetDogRecord dog) {
        if (dog.getExploreDurationHours() != null && isSupportedExploreDuration(dog.getExploreDurationHours())) {
            return dog.getExploreDurationHours();
        }
        if (dog.getExploreEndsAt() == null) {
            return EXPLORE_ONE_HOUR;
        }
        long durationMillis = dog.getExploreEndsAt() - dog.getUpdatedAt();
        if (durationMillis > midpointMillis(EXPLORE_EIGHT_HOURS, EXPLORE_TWELVE_HOURS)) {
            return EXPLORE_TWELVE_HOURS;
        }
        if (durationMillis > midpointMillis(EXPLORE_FOUR_HOURS, EXPLORE_EIGHT_HOURS)) {
            return EXPLORE_EIGHT_HOURS;
        }
        if (durationMillis > midpointMillis(EXPLORE_ONE_HOUR, EXPLORE_FOUR_HOURS)) {
            return EXPLORE_FOUR_HOURS;
        }
        return EXPLORE_ONE_HOUR;
    }

    private static boolean isSupportedExploreDuration(int durationHours) {
        return durationHours == EXPLORE_ONE_HOUR
                || durationHours == EXPLORE_FOUR_HOURS
                || durationHours == EXPLORE_EIGHT_HOURS
                || durationHours == EXPLORE_TWELVE_HOURS;
    }

    private static long midpointMillis(int leftHours, int rightHours) {
        return (leftHours + rightHours) * 60L * 60L * 1000L / 2L;
    }

    private static void ensureExploreStageUnlocked(PetDogRecord dog, int durationHours) {
        if (durationHours == EXPLORE_EIGHT_HOURS && !"adult".equals(dog.getStage())
                && !"champion".equals(dog.getStage())) {
            throw new IllegalArgumentException("成犬后才能派遣 8 小时探险");
        }
        if (durationHours == EXPLORE_TWELVE_HOURS && !"champion".equals(dog.getStage())) {
            throw new IllegalArgumentException("冠军犬才能派遣 12 小时探险");
        }
    }

    private static void resetInvalidExploreAndThrow(SqlSession session,
                                                    PetDogMapper dogMapper,
                                                    String dogId,
                                                    long accountId,
                                                    long now) {
        if (dogMapper.resetExplore(dogId, accountId, now) <= 0) {
            throw new IllegalArgumentException("探险数据异常，请刷新后重试");
        }
        session.commit();
        throw new IllegalArgumentException(INVALID_EXPLORE_RESET_ERROR);
    }

    private static void applyExploreRolls(SqlSession session, long accountId, int durationHours,
                                          List<PetExploreRewardDTO> rewards, String today, long now) {
        PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        Map<String, Integer> itemCounts = exploreItemCounts(itemMapper, accountId);
        for (int i = 0; i < exploreRollCount(durationHours); i++) {
            int roll = nextExploreRoll();
            if (roll < 50) {
                applyExploreItemReward(assetsMapper, counterMapper, itemMapper, accountId,
                        BACK_HILL_NORMAL_ITEM_IDS, itemCounts, rewards, today, now);
            } else if (roll < 58) {
                applyExploreItemReward(assetsMapper, counterMapper, itemMapper, accountId,
                        BACK_HILL_RARE_ITEM_IDS, itemCounts, rewards, today, now);
            } else if (roll < 78) {
                applyExploreCollectionReward(session, accountId, rewards, now);
            } else if (roll < 80) {
                applyExploreTreasureMapReward(session, accountId, rewards, now);
            } else {
                addExploreBones(assetsMapper, accountId, EXPLORE_ROLL_BONES, rewards, now);
            }
        }
    }

    private static void applyExploreCollectionReward(SqlSession session, long accountId,
                                                     List<PetExploreRewardDTO> rewards, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        String itemId = pickBackHillCollectionItem(collectionMapper.listByAccountId(accountId));
        collectionMapper.addCollection(accountId, itemId, now);
        rewards.add(new PetExploreRewardDTO("collection", itemId, 1));
    }

    private static void applyExploreTreasureMapReward(SqlSession session, long accountId,
                                                      List<PetExploreRewardDTO> rewards, long now) {
        session.getMapper(PetCollectionMapper.class).addCollection(
                accountId, TREASURE_MAP_FRAGMENT_COLLECTION_ID, now);
        rewards.add(new PetExploreRewardDTO("collection", TREASURE_MAP_FRAGMENT_COLLECTION_ID, 1));
    }

    private static String pickBackHillCollectionItem(List<PetCollectionRecord> collections) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetCollectionRecord collection : collections) {
            counts.put(collection.getItemId(), collection.getCount());
        }
        String selected = BACK_HILL_COLLECTION_ITEM_IDS.get(0);
        int selectedCount = Integer.MAX_VALUE;
        for (String itemId : BACK_HILL_COLLECTION_ITEM_IDS) {
            int count = counts.getOrDefault(itemId, 0);
            if (count < selectedCount) {
                selected = itemId;
                selectedCount = count;
            }
        }
        return selected;
    }

    private static void applyExploreItemReward(PetAssetsMapper assetsMapper, PetDailyCounterMapper counterMapper,
                                               PetItemMapper itemMapper, long accountId, List<String> pool,
                                               Map<String, Integer> itemCounts,
                                               List<PetExploreRewardDTO> rewards, String today, long now) {
        String itemId = pickAvailableLuckyBagItem(pool, itemCounts);
        if (itemId == null) {
            addExploreBones(assetsMapper, accountId, EXPLORE_ITEM_OVERFLOW_BONES, rewards, now);
            return;
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_EXPLORE_ITEM_GAIN,
                DAILY_EXPLORE_ITEM_GAIN_LIMIT, now) <= 0) {
            addExploreBones(assetsMapper, accountId, EXPLORE_ITEM_OVERFLOW_BONES, rewards, now);
            return;
        }
        if (itemMapper.addItemIfUnderLimit(accountId, itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
            addExploreBones(assetsMapper, accountId, EXPLORE_ITEM_OVERFLOW_BONES, rewards, now);
            return;
        }
        itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + 1);
        rewards.add(new PetExploreRewardDTO("item", itemId, 1));
    }

    private static void addExploreBones(PetAssetsMapper assetsMapper, long accountId, int amount,
                                        List<PetExploreRewardDTO> rewards, long now) {
        assetsMapper.addBones(accountId, amount, now);
        rewards.add(new PetExploreRewardDTO("bones", null, amount));
    }

    private static Map<String, Integer> exploreItemCounts(PetItemMapper itemMapper, long accountId) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetItemRecord item : itemMapper.listPositiveByAccountId(accountId)) {
            if (BACK_HILL_NORMAL_ITEM_IDS.contains(item.getItemId())
                    || BACK_HILL_RARE_ITEM_IDS.contains(item.getItemId())) {
                counts.put(item.getItemId(), item.getCount());
            }
        }
        return counts;
    }

    private static int nextExploreRoll() {
        return exploreRollSupplier.getAsInt();
    }

    private static Map<String, Integer> luckyBagItemCounts(PetItemMapper itemMapper, long accountId) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetItemRecord item : itemMapper.listPositiveByAccountId(accountId)) {
            if (LUCKY_BAG_ITEM_IDS.contains(item.getItemId())) {
                counts.put(item.getItemId(), item.getCount());
            }
        }
        return counts;
    }

    private static String rollLuckyBagItem(Map<String, Integer> itemCounts) {
        int rarityRoll = ThreadLocalRandom.current().nextInt(100);
        List<String> pool;
        if (rarityRoll < 70) {
            pool = LUCKY_BAG_NORMAL_ITEM_IDS;
        } else if (rarityRoll < 95) {
            pool = LUCKY_BAG_RARE_ITEM_IDS;
        } else {
            pool = LUCKY_BAG_EPIC_ITEM_IDS;
        }

        String itemId = pickAvailableLuckyBagItem(pool, itemCounts);
        if (itemId != null) {
            return itemId;
        }
        return pickAvailableLuckyBagItem(LUCKY_BAG_ITEM_IDS, itemCounts);
    }

    private static String pickAvailableLuckyBagItem(List<String> pool, Map<String, Integer> itemCounts) {
        List<String> availableItems = new ArrayList<>(pool.size());
        for (String itemId : pool) {
            if (itemCounts.getOrDefault(itemId, 0) < MAX_ITEM_COUNT) {
                availableItems.add(itemId);
            }
        }
        if (availableItems.isEmpty()) {
            return null;
        }
        return availableItems.get(ThreadLocalRandom.current().nextInt(availableItems.size()));
    }

    private static PetProfileDTO buySlotLocked(long accountId) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if (mapper.buySecondDogSlotIfAffordable(accountId, SECOND_DOG_SLOT_PRICE, now) <= 0) {
                if (assets.getDogSlots() >= MAX_DOG_SLOTS) {
                    throw new IllegalArgumentException("狗位已达上限");
                }
                throw new IllegalArgumentException("骨头币不足");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO checkinLocked(long accountId) {
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
            if (checkinMapper.findByAccountIdAndDate(accountId, today) != null) {
                throw new IllegalArgumentException("今天已经签到过了");
            }

            int cycleDay = checkinMapper.countByAccountId(accountId) % 7 + 1;
            ensureAssets(session, accountId);
            checkinMapper.insert(PetCheckinRecord.builder()
                    .accountId(accountId)
                    .checkinDate(today)
                    .cycleDay(cycleDay)
                    .createdAt(now)
                    .build());
            applyCheckinReward(session, accountId, cycleDay, now);
            applyCompanionBondReward(session, accountId, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO makeupCheckinLocked(long accountId, PetMakeupCheckinDTO request) {
        LocalDate checkinDate = parseMakeupCheckinDate(request);
        LocalDate today = LocalDate.now();
        if (!checkinDate.isBefore(today)
                || checkinDate.getYear() != today.getYear()
                || checkinDate.getMonth() != today.getMonth()) {
            throw new IllegalArgumentException("只能补签当前月份内早于今天的日期");
        }

        String checkinDateText = checkinDate.toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
            if (checkinMapper.findByAccountIdAndDate(accountId, checkinDateText) != null) {
                throw new IllegalArgumentException("该日期已经签到过了");
            }

            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            ensureAssets(session, accountId);
            if (assetsMapper.decrementMakeupCardsIfEnough(accountId, now) <= 0) {
                throw new IllegalArgumentException("补签卡不足");
            }

            int cycleDay = checkinMapper.countByAccountId(accountId) % 7 + 1;
            checkinMapper.insert(PetCheckinRecord.builder()
                    .accountId(accountId)
                    .checkinDate(checkinDateText)
                    .cycleDay(cycleDay)
                    .createdAt(now)
                    .build());
            applyCheckinReward(session, accountId, cycleDay, now);
            applyCompanionBondReward(session, accountId, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static LocalDate parseMakeupCheckinDate(PetMakeupCheckinDTO request) {
        String checkinDate = request == null ? null : StrUtil.trim(request.getCheckinDate());
        if (StrUtil.isBlank(checkinDate)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }
        try {
            return LocalDate.parse(checkinDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("狗狗请求内容无效", e);
        }
    }

    private static void applyCheckinReward(SqlSession session, long accountId, int cycleDay, long now) {
        PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
        switch (cycleDay) {
            case 1:
                mapper.addBones(accountId, 20, now);
                break;
            case 2:
                mapper.addBones(accountId, 30, now);
                break;
            case 3:
                mapper.addFood(accountId, 2, now);
                break;
            case 4:
                mapper.addBones(accountId, 50, now);
                break;
            case 5:
                mapper.addFood(accountId, 3, now);
                break;
            case 6:
                mapper.addBones(accountId, 80, now);
                break;
            case 7:
                applySeventhDayCheckinReward(session, accountId, now);
                break;
            default:
                break;
        }
    }

    private static void applySeventhDayCheckinReward(SqlSession session, long accountId, long now) {
        PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        assetsMapper.addBones(accountId, SEVENTH_DAY_CHECKIN_BONES, now);

        Map<String, Integer> itemCounts = normalItemCounts(itemMapper, accountId);
        int overflowItemCount = 0;
        for (int i = 0; i < SEVENTH_DAY_CHECKIN_NORMAL_ITEM_COUNT; i++) {
            String itemId = pickAvailableLuckyBagItem(LUCKY_BAG_NORMAL_ITEM_IDS, itemCounts);
            if (itemId == null) {
                overflowItemCount++;
                continue;
            }
            if (itemMapper.addItemIfUnderLimit(accountId, itemId, 1, MAX_ITEM_COUNT, now) > 0) {
                itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + 1);
            } else {
                overflowItemCount++;
            }
        }

        if (overflowItemCount > 0) {
            assetsMapper.addBones(accountId, overflowItemCount * CHECKIN_ITEM_OVERFLOW_BONES, now);
        }
    }

    private static Map<String, Integer> normalItemCounts(PetItemMapper itemMapper, long accountId) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetItemRecord item : itemMapper.listPositiveByAccountId(accountId)) {
            if (LUCKY_BAG_NORMAL_ITEM_IDS.contains(item.getItemId())) {
                counts.put(item.getItemId(), item.getCount());
            }
        }
        return counts;
    }

    private static void applyCompanionBondReward(SqlSession session, long accountId, long now) {
        PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
        List<PetDogRecord> dogs = dogMapper.listByOwner(accountId);
        if (dogs.isEmpty()) {
            return;
        }

        PetAssetsRecord assets = findAssetsOrDefault(session, accountId);
        PetDogRecord dog = resolveCompanionDog(assets.getCompanionDogId(), dogs);
        dogMapper.updateCareStats(dog.getId(), accountId, Math.min(100, dog.getBond() + 10),
                dog.getEnergy(), now);
    }

    private static PetDogRecord resolveCompanionDog(String savedDogId, List<PetDogRecord> dogs) {
        if (StrUtil.isNotBlank(savedDogId)) {
            for (PetDogRecord dog : dogs) {
                if (savedDogId.equals(dog.getId())) {
                    return dog;
                }
            }
        }
        return dogs.get(0);
    }

    private static PetAssetsRecord ensureAssets(SqlSession session, long accountId) {
        PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
        PetAssetsRecord assets = mapper.findByAccountId(accountId);
        if (assets != null) {
            return assets;
        }

        long now = System.currentTimeMillis();
        assets = PetAssetsRecord.builder()
                .accountId(accountId)
                .bones(DEFAULT_BONES)
                .food(DEFAULT_FOOD)
                .makeupCards(DEFAULT_MAKEUP_CARDS)
                .dogSlots(DEFAULT_DOG_SLOTS)
                .energyLimit(DEFAULT_ENERGY_LIMIT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mapper.insert(assets);
        return assets;
    }

    private static boolean refreshExpiredDogEnergy(PetDogMapper dogMapper, long accountId, int energyLimit,
                                                   String today, long now) {
        return dogMapper.refreshExpiredEnergy(accountId, energyLimit, today, now) > 0;
    }

    private static PetAssetsRecord findAssetsOrDefault(SqlSession session, long accountId) {
        PetAssetsRecord assets = session.getMapper(PetAssetsMapper.class).findByAccountId(accountId);
        if (assets != null) {
            return assets;
        }
        return PetAssetsRecord.builder()
                .accountId(accountId)
                .bones(DEFAULT_BONES)
                .food(DEFAULT_FOOD)
                .makeupCards(DEFAULT_MAKEUP_CARDS)
                .dogSlots(DEFAULT_DOG_SLOTS)
                .energyLimit(DEFAULT_ENERGY_LIMIT)
                .build();
    }

    private static Object accountLock(long accountId) {
        return ACCOUNT_LOCKS.computeIfAbsent(accountId, ignored -> new Object());
    }

    private static int findDailyCounterValue(PetDailyCounterMapper mapper, long accountId,
                                             String date, String counter) {
        Integer value = mapper.findValue(accountId, date, counter);
        return value == null ? 0 : Math.max(0, value);
    }

    private static int findCollectionCount(PetCollectionMapper mapper, long accountId, String itemId) {
        Integer value = mapper.findCount(accountId, itemId);
        return value == null ? 0 : Math.max(0, value);
    }

    private static boolean isUniqueConstraint(PersistenceException e) {
        String message = e.getMessage();
        return message != null && message.contains("SQLITE_CONSTRAINT");
    }

    private static List<String> createLuckyBagItemIds() {
        List<String> itemIds = new ArrayList<>();
        itemIds.addAll(LUCKY_BAG_NORMAL_ITEM_IDS);
        itemIds.addAll(LUCKY_BAG_RARE_ITEM_IDS);
        itemIds.addAll(LUCKY_BAG_EPIC_ITEM_IDS);
        return Collections.unmodifiableList(itemIds);
    }

    private static Map<String, Integer> createSellItemPrices() {
        Map<String, Integer> prices = new java.util.HashMap<>();
        for (String itemId : SHOP_NORMAL_ITEM_IDS) {
            prices.put(itemId, SELL_NORMAL_ITEM_PRICE);
        }
        for (String itemId : LUCKY_BAG_RARE_ITEM_IDS) {
            prices.put(itemId, SELL_RARE_ITEM_PRICE);
        }
        for (String itemId : LUCKY_BAG_EPIC_ITEM_IDS) {
            prices.put(itemId, SELL_EPIC_ITEM_PRICE);
        }
        return Collections.unmodifiableMap(prices);
    }

    private static PetDogDTO toDTO(PetDogRecord row) {
        return new PetDogDTO(row.getId(), row.getName(), row.getBreed(), row.getStage(),
                row.getSpeed(), row.getStamina(), row.getBurst(), row.getWisdom(), row.getBond(),
                row.getEnergy(), row.getStatus(), row.getExploreLocation(), row.getExploreEndsAt(),
                row.getRaceCount(), row.getRaceFirstCount());
    }

    private static boolean updateDogGrowthStages(PetDogMapper dogMapper, long accountId,
                                                 List<PetDogRecord> dogs, long now) {
        boolean changed = false;
        for (PetDogRecord dog : dogs) {
            changed = updateDogGrowthStage(dogMapper, accountId, dog, now) || changed;
        }
        return changed;
    }

    private static boolean updateDogGrowthStage(PetDogMapper dogMapper, long accountId,
                                                PetDogRecord dog, long now) {
        String nextStage = resolveDogGrowthStage(dog);
        if (nextStage.equals(dog.getStage())) {
            return false;
        }
        dogMapper.updateStage(dog.getId(), accountId, nextStage, now);
        dog.setStage(nextStage);
        return true;
    }

    private static String resolveDogGrowthStage(PetDogRecord dog) {
        String currentStage = StrUtil.blankToDefault(dog.getStage(), DOG_STAGE_PUPPY);
        int totalStats = dog.getSpeed() + dog.getStamina() + dog.getBurst() + dog.getWisdom() + dog.getBond();
        if (DOG_STAGE_CHAMPION.equals(currentStage)
                || (totalStats >= DOG_CHAMPION_TOTAL_STATS_THRESHOLD
                && dog.getRaceFirstCount() >= DOG_CHAMPION_RACE_FIRST_COUNT_THRESHOLD)) {
            return DOG_STAGE_CHAMPION;
        }
        if (DOG_STAGE_ADULT.equals(currentStage)
                || (totalStats >= DOG_ADULT_TOTAL_STATS_THRESHOLD
                && dog.getRaceCount() >= DOG_ADULT_RACE_COUNT_THRESHOLD)) {
            return DOG_STAGE_ADULT;
        }
        return currentStage;
    }

    private static PetAssetsDTO toDTO(PetAssetsRecord row) {
        return new PetAssetsDTO(row.getBones(), row.getFood(), row.getMakeupCards(), row.getDogSlots(),
                row.getEnergyLimit());
    }

    private static PetInventoryItemDTO toDTO(PetItemRecord row) {
        return new PetInventoryItemDTO(row.getItemId(), row.getCount());
    }

    private static PetCollectionItemDTO toDTO(PetCollectionRecord row) {
        return new PetCollectionItemDTO(row.getItemId(), row.getCount(), row.isDiscovered());
    }

    private static String resolveCompanionDogId(String savedDogId, List<PetDogDTO> dogs) {
        if (dogs.isEmpty()) {
            return null;
        }
        if (StrUtil.isNotBlank(savedDogId)) {
            for (PetDogDTO dog : dogs) {
                if (savedDogId.equals(dog.getId())) {
                    return savedDogId;
                }
            }
        }
        return dogs.get(0).getId();
    }

    private static final class BreedStats {
        private final boolean hidden;
        private final int speed;
        private final int stamina;
        private final int burst;
        private final int wisdom;
        private final int bond;

        private BreedStats(boolean hidden, int speed, int stamina, int burst, int wisdom, int bond) {
            this.hidden = hidden;
            this.speed = speed;
            this.stamina = stamina;
            this.burst = burst;
            this.wisdom = wisdom;
            this.bond = bond;
        }

        private static BreedStats of(String breed) {
            if ("corgi".equals(breed)) {
                return new BreedStats(false, 8, 12, 10, 10, 10);
            }
            if ("golden".equals(breed)) {
                return new BreedStats(false, 8, 10, 8, 10, 14);
            }
            if ("border_collie".equals(breed)) {
                return new BreedStats(false, 10, 10, 8, 15, 7);
            }
            if ("greyhound".equals(breed)) {
                return new BreedStats(false, 15, 10, 12, 8, 5);
            }
            if ("poodle".equals(breed)) {
                return new BreedStats(false, 12, 8, 12, 10, 8);
            }
            if ("native".equals(breed)) {
                return new BreedStats(false, 10, 14, 10, 8, 8);
            }
            if ("shiba".equals(breed) || "husky".equals(breed)) {
                return new BreedStats(true, 0, 0, 0, 0, 0);
            }
            return null;
        }
    }

}
