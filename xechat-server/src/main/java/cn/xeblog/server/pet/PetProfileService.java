package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetCheckinMilestoneRewardDTO;
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
import cn.xeblog.commons.entity.pet.PetInteractionStatusDTO;
import cn.xeblog.commons.entity.pet.PetMakeupCheckinDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetRenameDTO;
import cn.xeblog.commons.entity.pet.PetSellItemDTO;
import cn.xeblog.commons.entity.pet.PetSetCompanionDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillActionDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDefinitionDTO;
import cn.xeblog.commons.entity.pet.PetTrainingStatusDTO;
import cn.xeblog.commons.entity.pet.PetUseItemDTO;
import cn.xeblog.commons.entity.pet.PetWalkDogDTO;
import cn.xeblog.commons.enums.Game;
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
    private static final int DAILY_DOG_BOND_LIMIT = 4;
    private static final String DOG_STAGE_PUPPY = "puppy";
    private static final String DOG_STAGE_ADULT = "adult";
    private static final String DOG_STAGE_CHAMPION = "champion";
    private static final int DOG_STAT_MIN = 0;
    private static final int DOG_STAT_MAX = 100;
    private static final int DOG_ADULT_TOTAL_STATS_THRESHOLD = 150;
    private static final int DOG_ADULT_RACE_COUNT_THRESHOLD = 3;
    private static final int DOG_CHAMPION_TOTAL_STATS_THRESHOLD = 300;
    private static final int DOG_CHAMPION_RACE_FIRST_COUNT_THRESHOLD = 1;
    private static final int MINI_GAME_MIN_REWARD_SECONDS = 60;
    private static final int MINI_GAME_COMPLETE_BONES = 10;
    private static final int DAILY_MINI_GAME_COMPLETE_REWARD_LIMIT = 5;
    private static final int MINI_GAME_WIN_BONES = 20;
    private static final int DAILY_MINI_GAME_WIN_REWARD_LIMIT = 3;
    private static final int INTERACTION_DAILY_BONUS_CAP = 150;
    private static final int INTERACTION_ITEM_DAILY_REWARD_LIMIT = 2;
    private static final int DOG_RACE_DOG_COUNT = 5;
    private static final String SHOP_ITEM_FOOD = "food";
    private static final String SHOP_ITEM_MAKEUP_CARD = "makeup_card";
    private static final String SHOP_ITEM_LUCKY_BAG = "lucky_bag";
    private static final int SHOP_FOOD_PRICE = 30;
    private static final int SHOP_FOOD_CREEK_DISCOUNT_PRICE = 25;
    private static final int SHOP_MAKEUP_CARD_PRICE = 150;
    private static final int SHOP_NORMAL_ITEM_PRICE = 80;
    private static final int SHOP_DAILY_RARE_ITEM_PRICE = 320;
    private static final int SHOP_LUCKY_BAG_PRICE = 250;
    private static final int SELL_NORMAL_ITEM_PRICE = 20;
    private static final int SELL_RARE_ITEM_PRICE = 80;
    private static final int SELL_EPIC_ITEM_PRICE = 200;
    private static final int MAX_FOOD = 99;
    private static final int MAX_MAKEUP_CARDS = 3;
    private static final int MAX_ITEM_COUNT = 9;
    private static final int MONTHLY_MAKEUP_CARD_BUY_LIMIT = 2;
    private static final int DAILY_NORMAL_ITEM_BUY_LIMIT = 3;
    private static final int DAILY_RARE_ITEM_BUY_LIMIT = 1;
    private static final int DAILY_LUCKY_BAG_BUY_LIMIT = 2;
    private static final int SEVENTH_DAY_CHECKIN_BONES = 100;
    private static final int SEVENTH_DAY_CHECKIN_NORMAL_ITEM_COUNT = 2;
    private static final int CHECKIN_ITEM_OVERFLOW_BONES = 10;
    private static final int CHECKIN_MILESTONE_INTERVAL = 28;
    private static final int CHECKIN_MILESTONE_RARE_ITEM_OVERFLOW_BONES = 80;
    private static final int BACK_HILL_COLLECTION_CHECKIN_BONUS_BONES = 5;
    private static final List<String> CHECKIN_MILESTONE_DECORATION_IDS = Collections.unmodifiableList(Arrays.asList(
            "checkin_decoration_hat",
            "checkin_decoration_scarf",
            "checkin_decoration_sunhat",
            "checkin_decoration_sunglasses",
            "checkin_decoration_ribbon",
            "checkin_decoration_cap"
    ));
    private static final String TRAINING_DEFINITION_VERSION = "v5-explore-training";
    private static final String TRAINING_SKILL_ROUTE = "explore_route";
    private static final String TRAINING_SKILL_TREASURE = "explore_treasure";
    private static final String TRAINING_SKILL_COLLECTION = "explore_collection";
    private static final String TRAINING_SKILL_BONES = "explore_bones";
    private static final String TRAINING_SKILL_ENERGY = "explore_energy";
    private static final List<Integer> TRAINING_UPGRADE_COSTS = Collections.unmodifiableList(
            Arrays.asList(100, 150, 300, 500, 800));
    private static final List<PetTrainingSkillDefinitionDTO> TRAINING_SKILL_DEFINITIONS =
            Collections.unmodifiableList(createTrainingSkillDefinitions());
    private static final Map<String, List<Integer>> TRAINING_SKILL_EFFECTS =
            Collections.unmodifiableMap(createTrainingSkillEffects());
    private static final String EXPLORE_LOCATION_BACK_HILL = "back_hill";
    private static final String EXPLORE_LOCATION_MYSTERY_CAVE = "mystery_cave";
    private static final String ITEM_BACK_HILL_CHEST = "chest_back_hill";
    private static final int MAX_BACK_HILL_CHEST_COUNT = 99;
    private static final String BACK_HILL_CHEST_FULL_ERROR = "后山箱子已达上限，打开后再探险";
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
    private static final int EXPLORE_EASTER_EVENT_OVERFLOW_BONES = 50;
    private static final int HUSKY_TREASURE_MAP_FRAGMENT_LIMIT = 3;
    private static final int DAILY_EXPLORE_START_LIMIT = 3;
    private static final int DAILY_EXPLORE_ITEM_GAIN_LIMIT = 5;
    private static final String TREASURE_MAP_FRAGMENT_COLLECTION_ID = "treasure_map_fragment";
    private static final String MYSTERY_CAVE_COMPLETED_COLLECTION_ID = "mystery_cave_completed";
    private static final String EASTER_NEIGHBOR_SLIPPER_COLLECTION_ID = "easter_neighbor_slipper";
    private static final String EASTER_SNAIL_COLLECTION_ID = "easter_snail";
    private static final int EASTER_NEIGHBOR_SLIPPER_CHECKIN_BONES = 50;
    private static final String ITEM_FEAST = "item_feast";
    private static final String ITEM_EXPRESS = "item_express";
    private static final String ITEM_LUCKY_DAY = "item_lucky_day";
    private static final int LUCKY_DAY_REWARD_MULTIPLIER = 2;
    private static final String DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX = "bond_total:";
    private static final String DAILY_COUNTER_GREET_BOND_PREFIX = "bond_greet:";
    private static final String DAILY_COUNTER_GAME_BOND_PREFIX = "bond_game:";
    private static final String DAILY_COUNTER_FEED_FOOD = "feed_food";
    private static final String DAILY_COUNTER_FEED_BOND_PREFIX = "feed_bond:";
    private static final String DAILY_COUNTER_OUTING_BOND_PREFIX = "bond_outing:";
    private static final String DAILY_COUNTER_USE_ITEM_FEAST = "use_item_feast";
    private static final String DAILY_COUNTER_USE_ITEM_EXPRESS = "use_item_express";
    private static final String DAILY_COUNTER_USE_ITEM_LUCKY_DAY = "use_item_lucky_day";
    private static final String DAILY_COUNTER_SHOP_NORMAL_ITEM_BUY = "shop_normal_item_buy";
    private static final String DAILY_COUNTER_SHOP_DAILY_RARE_ITEM_BUY = "shop_daily_rare_item_buy";
    private static final String DAILY_COUNTER_SHOP_LUCKY_BAG_BUY = "shop_lucky_bag_buy";
    private static final String DAILY_COUNTER_EXPLORE_START = "explore_start";
    private static final String DAILY_COUNTER_EXPLORE_ITEM_GAIN = "explore_item_gain";
    private static final String DAILY_COUNTER_MINI_GAME_COMPLETE = "mini_game_complete";
    private static final String DAILY_COUNTER_MINI_GAME_WIN = "mini_game_win";
    private static final String DAILY_COUNTER_MINI_GAME_FIRST_WIN = "mini_game_first_win";
    private static final String DAILY_COUNTER_INTERACTION_BONUS_BONES = "interaction_bonus_bones";
    private static final String DAILY_COUNTER_INTERACTION_ITEM_PREFIX = "interaction_item_";
    private static final String MONTHLY_COUNTER_SHOP_MAKEUP_CARD_BUY = "shop_makeup_card_buy";
    private static final Map<String, Integer> INTERACTION_ITEM_REWARD_BONES = interactionItemRewardBones();
    private static final List<String> LUCKY_BAG_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
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
    private static final List<String> LUCKY_BAG_RARE_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
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
    private static final List<String> LUCKY_BAG_EPIC_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_wild_common",
            "item_party_equalizer",
            "item_gift_pack",
            "item_express",
            "item_lucky_day"
    ));
    private static final List<String> BACK_HILL_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_hint",
            "item_time",
            "item_sync_prophecy"
    ));
    private static final List<String> BACK_HILL_RARE_ITEM_IDS = Collections.unmodifiableList(Collections.singletonList(
            "item_turtle_probe"
    ));
    private static final List<String> BACK_HILL_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "back_hill_ball",
            "back_hill_branch",
            "back_hill_leaf",
            "back_hill_stone",
            "back_hill_mushroom",
            "back_hill_feather"
    ));
    private static final List<String> CREEK_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "creek_shell",
            "creek_snail",
            "creek_lotus",
            "creek_duck",
            "creek_coral",
            "creek_drop"
    ));
    private static final List<String> OLD_LIBRARY_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "old_library_scroll",
            "old_library_pen",
            "old_library_key",
            "old_library_candle",
            "old_library_book",
            "old_library_bookmark"
    ));
    private static final int OLD_LIBRARY_COLLECTION_BASE_BONES_BONUS_PERCENT = 10;
    private static final List<String> SNOW_MOUNTAIN_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "snow_mountain_snowflake",
            "snow_mountain_ice",
            "snow_mountain_skate",
            "snow_mountain_cloud",
            "snow_mountain_board",
            "snow_mountain_deer"
    ));
    private static final int SNOW_MOUNTAIN_COLLECTION_ENERGY_LIMIT_BONUS = 10;
    private static final int COLLECTION_SELL_PRICE = 15;
    private static final Set<String> SELLABLE_COLLECTION_ITEM_IDS = Collections.unmodifiableSet(
            new HashSet<>(BACK_HILL_COLLECTION_ITEM_IDS));

    private static List<PetTrainingSkillDefinitionDTO> createTrainingSkillDefinitions() {
        List<PetTrainingSkillDefinitionDTO> definitions = new ArrayList<>();
        definitions.add(new PetTrainingSkillDefinitionDTO(TRAINING_SKILL_ROUTE, "熟路口令", "🧭",
                "探险耗时缩短", 5,
                Arrays.asList("耗时 -4%", "耗时 -7%", "耗时 -10%", "耗时 -13%", "耗时 -16%")));
        definitions.add(new PetTrainingSkillDefinitionDTO(TRAINING_SKILL_TREASURE, "寻宝训练", "🔎",
                "每次判定稀有卡概率提高", 5,
                Arrays.asList("稀有卡 +1pp", "稀有卡 +2pp", "稀有卡 +3pp", "稀有卡 +4pp", "稀有卡 +5pp")));
        definitions.add(new PetTrainingSkillDefinitionDTO(TRAINING_SKILL_COLLECTION, "收集训练", "🧺",
                "每次判定收藏品概率提高", 5,
                Arrays.asList("收藏品 +2pp", "收藏品 +3pp", "收藏品 +4pp", "收藏品 +5pp", "收藏品 +6pp")));
        definitions.add(new PetTrainingSkillDefinitionDTO(TRAINING_SKILL_BONES, "叼骨训练", "🦴",
                "本次保底骨头币增加", 5,
                Arrays.asList("保底骨头币 +5%", "保底骨头币 +10%", "保底骨头币 +15%",
                        "保底骨头币 +20%", "保底骨头币 +25%")));
        definitions.add(new PetTrainingSkillDefinitionDTO(TRAINING_SKILL_ENERGY, "节能训练", "🍃",
                "开箱时有概率返还 1 活力", 5,
                Arrays.asList("返还概率 10%", "返还概率 15%", "返还概率 20%",
                        "返还概率 25%", "返还概率 30%")));
        return definitions;
    }

    private static Map<String, List<Integer>> createTrainingSkillEffects() {
        Map<String, List<Integer>> effects = new HashMap<>();
        effects.put(TRAINING_SKILL_ROUTE, Arrays.asList(4, 7, 10, 13, 16));
        effects.put(TRAINING_SKILL_TREASURE, Arrays.asList(1, 2, 3, 4, 5));
        effects.put(TRAINING_SKILL_COLLECTION, Arrays.asList(2, 3, 4, 5, 6));
        effects.put(TRAINING_SKILL_BONES, Arrays.asList(5, 10, 15, 20, 25));
        effects.put(TRAINING_SKILL_ENERGY, Arrays.asList(10, 15, 20, 25, 30));
        return effects;
    }

    private static Map<String, Integer> interactionItemRewardBones() {
        Map<String, Integer> rewards = new HashMap<>();
        rewards.put("item_sync_prophecy", 20);
        rewards.put("item_sync_perspective", 30);
        rewards.put("item_quiz_duel", 30);
        rewards.put("item_gomoku_prediction", 50);
        rewards.put("item_battle_direct_hit", 40);
        rewards.put("item_prophecy", 50);
        return Collections.unmodifiableMap(rewards);
    }

    private static final List<String> LUCKY_BAG_ITEM_IDS = createLuckyBagItemIds();
    private static final Set<String> SHOP_NORMAL_ITEM_IDS = Collections.unmodifiableSet(
            new HashSet<>(LUCKY_BAG_NORMAL_ITEM_IDS));
    private static final Map<String, Integer> SELL_ITEM_PRICES = createSellItemPrices();
    private static final Map<Long, Object> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
    private static IntSupplier exploreRollSupplier = () -> ThreadLocalRandom.current().nextInt(100);
    private static IntSupplier exploreEasterEventSupplier = () -> ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);

    private PetProfileService() {
    }

    public static PetProfileDTO profile(long accountId) {
        synchronized (accountLock(accountId)) {
            return profileLocked(accountId);
        }
    }

    public static boolean hasPositiveItem(long accountId, String itemId) {
        String normalizedItemId = StrUtil.trim(itemId);
        if (accountId <= 0L || StrUtil.isBlank(normalizedItemId)) {
            return false;
        }
        synchronized (accountLock(accountId)) {
            try (SqlSession session = DbInitializer.factory().openSession(true)) {
                PetItemRecord item = session.getMapper(PetItemMapper.class)
                        .findByAccountIdAndItemId(accountId, normalizedItemId);
                return item != null && item.getCount() > 0;
            }
        }
    }

    private static PetProfileDTO profileLocked(long accountId) {
        return profileLocked(accountId, null);
    }

    private static PetProfileDTO profileLocked(long accountId, PetCheckinMilestoneRewardDTO lastMilestoneReward) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = findAssetsOrDefault(session, accountId);
            LocalDate today = LocalDate.now();
            String todayText = today.toString();
            long now = System.currentTimeMillis();
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            int energyLimit = effectiveEnergyLimit(collectionMapper, accountId, assets.getEnergyLimit());
            boolean dogEnergyRefreshed = refreshExpiredDogEnergy(dogMapper, accountId,
                    energyLimit, todayText, now);
            List<PetDogRecord> rows = dogMapper.listByOwner(accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetTrainingMapper trainingMapper = session.getMapper(PetTrainingMapper.class);
            boolean exploreSettled = settleEndedExploresAsChests(accountId, dogMapper, itemMapper,
                    trainingMapper, rows, now);
            if (exploreSettled) {
                rows = dogMapper.listByOwner(accountId);
            }
            boolean dogStageChanged = updateDogGrowthStages(dogMapper, accountId, rows, now);
            List<PetItemRecord> itemRows = itemMapper.listPositiveByAccountId(accountId);
            List<PetCollectionRecord> collectionRows = collectionMapper.listByAccountId(accountId);
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
            PetDailyCounterMapper dailyCounterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetCheckinRecord todayCheckin = checkinMapper.findByAccountIdAndDate(accountId, todayText);
            List<String> checkedDatesInMonth = checkinMapper.listDatesByAccountIdAndMonthPrefix(
                    accountId, todayText.substring(0, 7));
            int totalCheckins = checkinMapper.countByAccountId(accountId);
            int cycleDay = todayCheckin == null ? totalCheckins % 7 + 1
                    : todayCheckin.getCycleDay();
            PetProfileDTO profile = new PetProfileDTO();
            profile.setAccountId(accountId);
            profile.setAssets(toDTO(assets, energyLimit));
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
                    totalCheckins, checkinMilestoneRemaining(totalCheckins),
                    checkedDatesInMonth, lastMilestoneReward));
            int treasureMapFragments = Math.min(HUSKY_TREASURE_MAP_FRAGMENT_LIMIT,
                    findCollectionCount(collectionMapper, accountId, TREASURE_MAP_FRAGMENT_COLLECTION_ID));
            boolean mysteryCaveCompleted = findCollectionCount(collectionMapper, accountId,
                    MYSTERY_CAVE_COMPLETED_COLLECTION_ID) > 0;
            profile.setExploreStatus(new PetExploreStatusDTO(
                    DAILY_EXPLORE_START_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_START),
                    DAILY_EXPLORE_ITEM_GAIN_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_ITEM_GAIN),
                    treasureMapFragments,
                    treasureMapFragments >= HUSKY_TREASURE_MAP_FRAGMENT_LIMIT,
                    mysteryCaveCompleted,
                    mysteryCaveCompleted));
            profile.setInteractionStatus(buildInteractionStatus(dailyCounterMapper, accountId, todayText));
            profile.setTrainingStatus(buildTrainingStatus(trainingMapper, accountId));
            if (dogEnergyRefreshed || exploreSettled || dogStageChanged) {
                session.commit();
            }
            return profile;
        }
    }

    private static PetTrainingStatusDTO buildTrainingStatus(PetTrainingMapper mapper, long accountId) {
        List<PetTrainingSkillDefinitionDTO> definitions = new ArrayList<>();
        for (PetTrainingSkillDefinitionDTO definition : TRAINING_SKILL_DEFINITIONS) {
            definitions.add(new PetTrainingSkillDefinitionDTO(definition.getSkillId(), definition.getName(),
                    definition.getEmoji(), definition.getDescription(), definition.getMaxLevel(),
                    new ArrayList<>(definition.getLevelEffects())));
        }
        List<PetTrainingSkillDTO> skills = new ArrayList<>();
        for (PetTrainingSkillRecord row : mapper.listSkillsByAccountId(accountId)) {
            skills.add(new PetTrainingSkillDTO(row.getSkillId(), row.getLevel(), row.getDefinitionVersion()));
        }
        PetTrainingFlagRecord flags = mapper.findFlags(accountId);
        boolean freeLearnAvailable = flags != null && flags.getFirstExploreFreeAvailable() > 0
                && flags.getFirstExploreFreeUsed() == 0;
        return new PetTrainingStatusDTO(TRAINING_DEFINITION_VERSION,
                new ArrayList<>(TRAINING_UPGRADE_COSTS),
                definitions,
                skills,
                freeLearnAvailable);
    }

    public static boolean addDogBattleReward(long winnerAccountId, long loserAccountId, int winnerBones, int loserBones) {
        if (winnerAccountId <= 0 || loserAccountId <= 0 || winnerBones < 0 || loserBones < 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, winnerAccountId);
            ensureAssets(session, loserAccountId);
            PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
            if (winnerBones > 0 && mapper.addBones(winnerAccountId, winnerBones, now) <= 0) {
                return false;
            }
            if (loserBones > 0 && mapper.addBones(loserAccountId, loserBones, now) <= 0) {
                return false;
            }
            session.commit();
            return true;
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
        if (stats == null) {
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
            ensureAssets(session, accountId);
            if (stats.hidden && !isHiddenBreedUnlocked(session, mapper, accountId, breed)) {
                throw new IllegalArgumentException("该隐藏品种尚未解锁");
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
            int energyLimit = effectiveEnergyLimit(session.getMapper(PetCollectionMapper.class),
                    accountId, assets.getEnergyLimit());
            refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);
            PetDogRecord dog = StrUtil.isBlank(dogId) ? null : dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能喂自己的狗狗");
            }

            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            if (assetsMapper.decrementFoodIfEnough(accountId, now) <= 0) {
                throw new IllegalArgumentException("狗粮不足");
            }

            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                    DAILY_COUNTER_FEED_BOND_PREFIX, now);

            int energy = dog.getEnergy();
            if (dog.getEnergy() < energyLimit
                    && counterMapper.incrementIfUnderLimit(accountId, today,
                    DAILY_COUNTER_FEED_FOOD, DAILY_FEED_LIMIT, now) > 0) {
                energy = Math.min(energyLimit, dog.getEnergy() + 1);
            }
            dogMapper.updateCareStats(dog.getId(), accountId, bond, energy, now);
            session.commit();
        }

        return profile(accountId);
    }

    public static PetProfileDTO greetAllDogs(long accountId) {
        synchronized (accountLock(accountId)) {
            return greetAllDogsLocked(accountId);
        }
    }

    public static PetProfileDTO walkDog(long accountId, PetWalkDogDTO request) {
        synchronized (accountLock(accountId)) {
            return walkDogLocked(accountId, request);
        }
    }

    private static PetProfileDTO greetAllDogsLocked(long accountId) {
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            List<PetDogRecord> dogs = dogMapper.listByOwner(accountId);
            for (PetDogRecord dog : dogs) {
                int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                        DAILY_COUNTER_GREET_BOND_PREFIX, now);
                if (bond != dog.getBond()) {
                    dogMapper.updateCareStats(dog.getId(), accountId, bond, dog.getEnergy(), now);
                }
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO walkDogLocked(long accountId, PetWalkDogDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            int energyLimit = effectiveEnergyLimit(session.getMapper(PetCollectionMapper.class),
                    accountId, assets.getEnergyLimit());
            refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);
            PetDogRecord dog = StrUtil.isBlank(dogId) ? null : dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能带自己的狗狗散步");
            }
            if (!"idle".equals(dog.getStatus())) {
                throw new IllegalArgumentException("狗狗空闲时才能散步");
            }

            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            String outingCounter = DAILY_COUNTER_OUTING_BOND_PREFIX + dog.getId();
            if (findDailyCounterValue(counterMapper, accountId, today, outingCounter) > 0) {
                session.commit();
                return profile(accountId);
            }
            if (dog.getEnergy() <= 0) {
                throw new IllegalArgumentException("狗狗活力不足");
            }

            String totalCounter = DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX + dog.getId();
            if (findDailyCounterValue(counterMapper, accountId, today, totalCounter) >= DAILY_DOG_BOND_LIMIT) {
                session.commit();
                return profile(accountId);
            }
            if (counterMapper.incrementIfUnderLimit(accountId, today, outingCounter, 1, now) <= 0
                    || counterMapper.incrementIfUnderLimit(accountId, today,
                    totalCounter, DAILY_DOG_BOND_LIMIT, now) <= 0) {
                session.commit();
                return profile(accountId);
            }
            int bond = clampDogStat(dog.getBond() + 1);
            int energy = dog.getEnergy() - 1;
            if (dogMapper.updateCareStats(dog.getId(), accountId, bond, energy, now) <= 0) {
                throw new IllegalArgumentException("狗狗散步失败，请刷新后重试");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static boolean isHiddenBreedUnlocked(SqlSession session, PetDogMapper dogMapper,
                                                 long accountId, String breed) {
        if ("husky".equals(breed)) {
            return findCollectionCount(session.getMapper(PetCollectionMapper.class),
                    accountId, MYSTERY_CAVE_COMPLETED_COLLECTION_ID) > 0;
        }
        if ("shiba".equals(breed)) {
            int firstPlaceCount = 0;
            for (PetDogRecord dog : dogMapper.listByOwner(accountId)) {
                firstPlaceCount += Math.max(0, dog.getRaceFirstCount());
                if (firstPlaceCount >= 3) {
                    return true;
                }
            }
            return false;
        }
        return true;
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

    public static PetProfileDTO sellCollection(long accountId, PetSellItemDTO request) {
        synchronized (accountLock(accountId)) {
            return sellCollectionLocked(accountId, request);
        }
    }

    public static PetProfileDTO useItem(long accountId, PetUseItemDTO request) {
        synchronized (accountLock(accountId)) {
            return useItemLocked(accountId, request);
        }
    }

    public static PetExploreOpenResultDTO openBackHillChest(long accountId, PetUseItemDTO request) {
        synchronized (accountLock(accountId)) {
            return openBackHillChestLocked(accountId, request);
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

    public static PetProfileDTO trainingLearn(long accountId, PetTrainingSkillActionDTO request) {
        synchronized (accountLock(accountId)) {
            return trainingLearnLocked(accountId, request);
        }
    }

    public static PetProfileDTO trainingUpgrade(long accountId, PetTrainingSkillActionDTO request) {
        synchronized (accountLock(accountId)) {
            return trainingUpgradeLocked(accountId, request);
        }
    }

    public static PetProfileDTO trainingEquip(long accountId, PetTrainingSkillActionDTO request) {
        synchronized (accountLock(accountId)) {
            return trainingEquipLocked(accountId, request);
        }
    }

    public static PetProfileDTO recordRaceResult(long accountId, PetRaceResultDTO request) {
        synchronized (accountLock(accountId)) {
            return recordRaceResultLocked(accountId, request);
        }
    }

    public static PetProfileDTO changeBones(long accountId, int delta) {
        synchronized (accountLock(accountId)) {
            return changeBonesLocked(accountId, delta);
        }
    }

    public static PetProfileDTO applyGameTraining(long accountId, Game game, boolean win) {
        synchronized (accountLock(accountId)) {
            return applyGameTrainingLocked(accountId, game, win);
        }
    }

    public static PetProfileDTO applyMiniGameResult(long accountId, Game game, boolean win, long durationSeconds) {
        synchronized (accountLock(accountId)) {
            return applyMiniGameResultLocked(accountId, game, win, durationSeconds);
        }
    }

    public static PetProfileDTO applyInteractionItemReward(long accountId, String itemId, int requestedBones) {
        synchronized (accountLock(accountId)) {
            return applyInteractionItemRewardLocked(accountId, itemId, requestedBones);
        }
    }

    public static PetProfileDTO spendRaceSignup(long accountId, String dogId, int energyCost, int bonesCost) {
        synchronized (accountLock(accountId)) {
            return spendRaceSignupLocked(accountId, dogId, energyCost, bonesCost);
        }
    }

    public static PetDogDTO findRaceDog(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            List<PetDogRecord> dogs = dogMapper.listByOwner(accountId);
            session.commit();
            PetDogRecord fallback = null;
            for (PetDogRecord dog : dogs) {
                if (fallback == null) {
                    fallback = dog;
                }
                if ("idle".equals(dog.getStatus()) || dog.getStatus() == null) {
                    return toDTO(dog);
                }
            }
            return fallback == null ? null : toDTO(fallback);
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
        if (!isSupportedExploreLocation(location)) {
            throw new IllegalArgumentException("暂不支持该探险地点");
        }
        if (!isSupportedExploreDuration(durationHours)) {
            throw new IllegalArgumentException("暂不支持该探险时长");
        }
        if (EXPLORE_LOCATION_MYSTERY_CAVE.equals(location) && durationHours != EXPLORE_EIGHT_HOURS) {
            throw new IllegalArgumentException("神秘洞穴只能派遣 8 小时探险");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        int energyCost = exploreEnergyCost(durationHours);
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);
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
            TrainingSnapshot trainingSnapshot = buildExploreTrainingSnapshot(
                    session.getMapper(PetTrainingMapper.class), accountId, dog.getExploreSkillId());
            long exploreEndsAt = now + exploreDurationMillis(durationHours, trainingSnapshot);
            if (EXPLORE_LOCATION_MYSTERY_CAVE.equals(location)) {
                ensureMysteryCaveAvailable(session, accountId);
            } else {
                ensureExploreStageUnlocked(dog, durationHours);
                if (backHillChestCount(session.getMapper(PetItemMapper.class), accountId) >= MAX_BACK_HILL_CHEST_COUNT) {
                    throw new IllegalArgumentException(BACK_HILL_CHEST_FULL_ERROR);
                }
            }
            if (session.getMapper(PetDailyCounterMapper.class).incrementIfUnderLimit(accountId,
                    today, DAILY_COUNTER_EXPLORE_START, DAILY_EXPLORE_START_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日探险派遣次数已达上限");
            }
            if (dogMapper.startExplore(dogId, accountId, energyCost, location, exploreEndsAt,
                    durationHours, trainingSnapshot.skillId, trainingSnapshot.level,
                    trainingSnapshot.definitionVersion, now) <= 0) {
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
            String location = StrUtil.trim(dog.getExploreLocation());
            if (!isSupportedExploreLocation(location)) {
                resetInvalidExploreAndThrow(session, dogMapper, dogId, accountId, now);
            }

            int durationHours = inferExploreDurationHours(dog);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            addExploreBones(assetsMapper, accountId,
                    applyExploreBonesTraining(effectiveExploreBaseBones(collectionMapper, accountId,
                            exploreBaseBones(durationHours)), dog), rewards, now);
            applyExploreRolls(session, accountId, durationHours, dog, rewards, today, now);
            if (EXPLORE_LOCATION_MYSTERY_CAVE.equals(location)) {
                collectionMapper.addCollection(accountId, MYSTERY_CAVE_COMPLETED_COLLECTION_ID, now);
                rewards.add(new PetExploreRewardDTO("collection", MYSTERY_CAVE_COMPLETED_COLLECTION_ID, 1));
            }
            grantFirstExploreFreeLearnIfEligible(session.getMapper(PetTrainingMapper.class),
                    accountId, durationHours, now);
            if (dogMapper.openExplore(dogId, accountId, now) <= 0) {
                throw new IllegalArgumentException("探险开箱失败，请刷新后重试");
            }
            PetAssetsRecord assets = assetsMapper.findByAccountId(accountId);
            int energyLimit = effectiveEnergyLimit(collectionMapper, accountId, assets.getEnergyLimit());
            applyExploreEnergyTraining(dogMapper, accountId, dog, energyLimit, rewards, now);
            session.commit();
        }

        return new PetExploreOpenResultDTO(profile(accountId), rewards);
    }

    private static PetProfileDTO trainingLearnLocked(long accountId, PetTrainingSkillActionDTO request) {
        String skillId = request == null ? null : StrUtil.trim(request.getSkillId());
        ensureTrainingSkillExists(skillId);
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetTrainingMapper trainingMapper = session.getMapper(PetTrainingMapper.class);
            if (trainingMapper.findSkill(accountId, skillId) != null) {
                throw new IllegalArgumentException("该训练技能已学习");
            }
            trainingMapper.ensureFlags(accountId, now);
            PetTrainingFlagRecord flags = trainingMapper.findFlags(accountId);
            boolean useFreeLearn = flags != null && flags.getFirstExploreFreeAvailable() > 0
                    && flags.getFirstExploreFreeUsed() == 0;
            if (useFreeLearn) {
                if (trainingMapper.consumeFirstExploreFreeLearn(accountId, now) <= 0) {
                    throw new IllegalArgumentException("免费学习机会已失效");
                }
            } else {
                int price = TRAINING_UPGRADE_COSTS.get(0);
                if (session.getMapper(PetAssetsMapper.class).decrementBonesIfEnough(accountId, price, now) <= 0) {
                    throw new IllegalArgumentException("骨头币不足");
                }
            }
            trainingMapper.insertSkill(accountId, skillId, 1, TRAINING_DEFINITION_VERSION, now);
            session.commit();
        }
        return profile(accountId);
    }

    private static PetProfileDTO trainingUpgradeLocked(long accountId, PetTrainingSkillActionDTO request) {
        String skillId = request == null ? null : StrUtil.trim(request.getSkillId());
        ensureTrainingSkillExists(skillId);
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetTrainingMapper trainingMapper = session.getMapper(PetTrainingMapper.class);
            PetTrainingSkillRecord skill = trainingMapper.findSkill(accountId, skillId);
            if (skill == null) {
                throw new IllegalArgumentException("请先学习该训练技能");
            }
            if (skill.getLevel() >= TRAINING_UPGRADE_COSTS.size()) {
                throw new IllegalArgumentException("训练技能已满级");
            }
            int nextLevel = skill.getLevel() + 1;
            int price = TRAINING_UPGRADE_COSTS.get(nextLevel - 1);
            if (session.getMapper(PetAssetsMapper.class).decrementBonesIfEnough(accountId, price, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (trainingMapper.updateSkillLevel(accountId, skillId, nextLevel, TRAINING_DEFINITION_VERSION, now) <= 0) {
                throw new IllegalArgumentException("训练技能升级失败，请刷新后重试");
            }
            session.commit();
        }
        return profile(accountId);
    }

    private static PetProfileDTO trainingEquipLocked(long accountId, PetTrainingSkillActionDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        String skillId = request == null ? null : StrUtil.trim(request.getSkillId());
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }
        if (StrUtil.isNotBlank(skillId)) {
            ensureTrainingSkillExists(skillId);
        } else {
            skillId = null;
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能设置自己的狗狗训练技能");
            }
            if (skillId != null && session.getMapper(PetTrainingMapper.class).findSkill(accountId, skillId) == null) {
                throw new IllegalArgumentException("请先学习该训练技能");
            }
            if (dogMapper.updateExploreSkill(dogId, accountId, skillId, now) <= 0) {
                throw new IllegalArgumentException("训练技能装配失败，请刷新后重试");
            }
            session.commit();
        }
        return profile(accountId);
    }

    private static PetProfileDTO useItemLocked(long accountId, PetUseItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        Integer quantity = request == null ? null : request.getQuantity();
        if (StrUtil.isBlank(itemId)) {
            throw new IllegalArgumentException("道具不能为空");
        }
        if (quantity == null || quantity != 1) {
            throw new IllegalArgumentException("道具使用数量必须为 1");
        }
        if (ITEM_LUCKY_DAY.equals(itemId)) {
            return useLuckyDayItem(accountId);
        }
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗不能为空");
        }
        if (ITEM_FEAST.equals(itemId)) {
            return useFeastItem(accountId, dogId);
        }
        if (ITEM_EXPRESS.equals(itemId)) {
            return useExpressItem(accountId, dogId);
        }
        throw new IllegalArgumentException("暂不支持该道具");
    }

    private static PetProfileDTO useLuckyDayItem(long accountId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            if (counterMapper.incrementIfUnderLimit(accountId, today,
                    DAILY_COUNTER_USE_ITEM_LUCKY_DAY, 1, now) <= 0) {
                throw new IllegalArgumentException("今日狗运爆棚已使用");
            }
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (itemMapper.decrementItemIfEnough(accountId, ITEM_LUCKY_DAY, 1, now) <= 0) {
                throw new IllegalArgumentException("道具数量不足");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetExploreOpenResultDTO openBackHillChestLocked(long accountId, PetUseItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        Integer quantity = request == null ? null : request.getQuantity();
        if (!ITEM_BACK_HILL_CHEST.equals(itemId)) {
            throw new IllegalArgumentException("暂不支持该道具");
        }
        if (quantity == null || quantity != 1) {
            throw new IllegalArgumentException("道具使用数量必须为 1");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        List<PetExploreRewardDTO> rewards = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (itemMapper.decrementItemIfEnough(accountId, ITEM_BACK_HILL_CHEST, 1, now) <= 0) {
                throw new IllegalArgumentException("道具数量不足");
            }
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            addExploreBones(assetsMapper, accountId, effectiveExploreBaseBones(
                    session.getMapper(PetCollectionMapper.class), accountId, exploreBaseBones(EXPLORE_ONE_HOUR)),
                    rewards, now);
            applyExploreRolls(session, accountId, EXPLORE_ONE_HOUR, null, rewards, today, now);
            session.commit();
        }

        return new PetExploreOpenResultDTO(profile(accountId), rewards);
    }

    private static PetProfileDTO useFeastItem(long accountId, String dogId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能给自己的狗狗使用道具");
            }
            if (dog.getEnergy() >= energyLimit) {
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
            dogMapper.updateCareStats(dog.getId(), accountId, dog.getBond(), energyLimit, now);
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
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);
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
        int weeklyPoints = Math.max(request == null ? 0 : request.getWeeklyPoints(), 0);
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
            if (dogMapper.recordRaceResult(dogId, accountId, firstPlaceIncrement, weeklyPoints, now) <= 0) {
                throw new IllegalArgumentException("只能结算自己的狗狗赛跑结果");
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO changeBonesLocked(long accountId, int delta) {
        if (delta == 0) {
            return profile(accountId);
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
            if (delta > 0) {
                if (mapper.addBones(accountId, delta, now) <= 0) {
                    throw new IllegalArgumentException("更新骨头币失败");
                }
            } else if (mapper.decrementBonesIfEnough(accountId, -delta, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            session.commit();
        }
        return profile(accountId);
    }

    private static PetProfileDTO applyGameTrainingLocked(long accountId, Game game, boolean win) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            applyCompanionGameBondInSession(session, accountId, now, today);
            session.commit();
        }
        return profile(accountId);
    }

    private static PetProfileDTO applyMiniGameResultLocked(long accountId, Game game, boolean win,
                                                           long durationSeconds) {
        if (durationSeconds < MINI_GAME_MIN_REWARD_SECONDS) {
            return profile(accountId);
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            int rewardMultiplier = miniGameRewardMultiplier(counterMapper, accountId, today);
            if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_MINI_GAME_COMPLETE,
                    DAILY_MINI_GAME_COMPLETE_REWARD_LIMIT * rewardMultiplier, now) > 0) {
                assetsMapper.addBones(accountId, MINI_GAME_COMPLETE_BONES * rewardMultiplier, now);
            }
            if (win) {
                if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_MINI_GAME_FIRST_WIN,
                        1, now) > 0) {
                    assetsMapper.addMakeupCardsIfUnderLimit(accountId, 1, MAX_MAKEUP_CARDS, now);
                }
                if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_MINI_GAME_WIN,
                        DAILY_MINI_GAME_WIN_REWARD_LIMIT * rewardMultiplier, now) > 0) {
                    assetsMapper.addBones(accountId, MINI_GAME_WIN_BONES * rewardMultiplier, now);
                }
            }
            applyCompanionGameBondInSession(session, accountId, now, today);
            session.commit();
        }
        return profile(accountId);
    }

    private static int miniGameRewardMultiplier(PetDailyCounterMapper counterMapper, long accountId, String today) {
        return isLuckyDayActive(counterMapper, accountId, today) ? LUCKY_DAY_REWARD_MULTIPLIER : 1;
    }

    private static boolean isLuckyDayActive(PetDailyCounterMapper counterMapper, long accountId, String today) {
        Integer value = counterMapper.findValue(accountId, today, DAILY_COUNTER_USE_ITEM_LUCKY_DAY);
        return value != null && value > 0;
    }

    private static PetProfileDTO applyInteractionItemRewardLocked(long accountId, String itemId, int requestedBones) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            applyInteractionItemRewardInSession(session, accountId, itemId, requestedBones, now, today);
            session.commit();
        }
        return profile(accountId);
    }

    static int applyInteractionItemRewardInSession(SqlSession session, long accountId, String itemId,
                                                   int requestedBones, long now, String today) {
        String normalizedItemId = StrUtil.trim(itemId);
        Integer configuredReward = INTERACTION_ITEM_REWARD_BONES.get(normalizedItemId);
        if (configuredReward == null) {
            throw new IllegalArgumentException("该道具没有互动奖励");
        }
        if (requestedBones <= 0) {
            throw new IllegalArgumentException("互动奖励必须为正数");
        }

        ensureAssets(session, accountId);
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        int rewardedToday = findDailyCounterValue(counterMapper, accountId, today,
                DAILY_COUNTER_INTERACTION_BONUS_BONES);
        int acceptedReward = Math.min(Math.min(requestedBones, configuredReward),
                INTERACTION_DAILY_BONUS_CAP - rewardedToday);
        if (acceptedReward <= 0) {
            return 0;
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today,
                DAILY_COUNTER_INTERACTION_ITEM_PREFIX + normalizedItemId,
                INTERACTION_ITEM_DAILY_REWARD_LIMIT, now) <= 0) {
            return 0;
        }
        if (counterMapper.incrementByIfUnderLimit(accountId, today,
                DAILY_COUNTER_INTERACTION_BONUS_BONES,
                acceptedReward, INTERACTION_DAILY_BONUS_CAP, now) <= 0) {
            return 0;
        }
        session.getMapper(PetAssetsMapper.class).addBones(accountId, acceptedReward, now);
        return acceptedReward;
    }

    private static void applyCompanionGameBondInSession(SqlSession session, long accountId, long now, String today) {
        PetAssetsRecord assets = ensureAssets(session, accountId);
        PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
        int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
        refreshExpiredDogEnergy(dogMapper, accountId, energyLimit, today, now);

        List<PetDogRecord> dogs = dogMapper.listByOwner(accountId);
        if (dogs.isEmpty()) {
            return;
        }

        PetDogRecord dog = resolveCompanionDog(assets.getCompanionDogId(), dogs);
        if (!"idle".equals(StrUtil.blankToDefault(dog.getStatus(), "idle"))) {
            return;
        }

        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                DAILY_COUNTER_GAME_BOND_PREFIX, now);
        if (bond != dog.getBond()
                && dogMapper.updateCareStats(dog.getId(), accountId, bond, dog.getEnergy(), now) <= 0) {
            throw new IllegalArgumentException("陪玩亲密度更新失败");
        }
    }

    private static PetProfileDTO spendRaceSignupLocked(long accountId, String dogId, int energyCost, int bonesCost) {
        String normalizedDogId = StrUtil.trim(dogId);
        if (StrUtil.isBlank(normalizedDogId)) {
            throw new IllegalArgumentException("赛跑报名缺少狗狗");
        }
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            refreshExpiredDogEnergy(dogMapper, accountId, assets.getEnergyLimit(), today, now);
            PetDogRecord dog = dogMapper.findByIdAndOwner(normalizedDogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("狗狗不存在");
            }
            if (assets.getBones() < bonesCost) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (dog.getEnergy() < energyCost) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            if (assetsMapper.decrementBonesIfEnough(accountId, bonesCost, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (dogMapper.updateCareStats(dog.getId(), accountId, dog.getBond(), dog.getEnergy() - energyCost, now) <= 0) {
                throw new IllegalArgumentException("狗狗活力扣减失败");
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
        if (LUCKY_BAG_RARE_ITEM_IDS.contains(itemId)) {
            return buyDailyRareItem(accountId, itemId, quantity);
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

    private static PetProfileDTO sellCollectionLocked(long accountId, PetSellItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        int quantity = request == null ? 0 : request.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("出售数量必须为正整数");
        }
        if (!SELLABLE_COLLECTION_ITEM_IDS.contains(itemId)) {
            throw new IllegalArgumentException("暂不支持出售该收藏品");
        }

        long now = System.currentTimeMillis();
        long bonesValue = (long) COLLECTION_SELL_PRICE * quantity;
        if (bonesValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("出售数量过大");
        }
        int bones = (int) bonesValue;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            if (collectionMapper.decrementCollectionIfEnough(accountId, itemId, quantity, now) <= 0) {
                throw new IllegalArgumentException("收藏品数量不足");
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

            int unitPrice = hasCompletedCollectionSet(session.getMapper(PetCollectionMapper.class),
                    accountId, CREEK_COLLECTION_ITEM_IDS) ? SHOP_FOOD_CREEK_DISCOUNT_PRICE : SHOP_FOOD_PRICE;
            int price = unitPrice * quantity;
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

    private static PetProfileDTO buyDailyRareItem(long accountId, String itemId, int quantity) {
        LocalDate today = LocalDate.now();
        if (!dailyRareShopItemId(today).equals(itemId)) {
            throw new IllegalArgumentException("今日未出售该稀有道具");
        }
        if (quantity > DAILY_RARE_ITEM_BUY_LIMIT) {
            throw new IllegalArgumentException("今日稀有道具购买次数已达上限");
        }

        long now = System.currentTimeMillis();
        String todayText = today.toString();
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

            int price = SHOP_DAILY_RARE_ITEM_PRICE * quantity;
            if (assets.getBones() < price) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (counterMapper.incrementByIfUnderLimit(accountId, todayText, DAILY_COUNTER_SHOP_DAILY_RARE_ITEM_BUY,
                    quantity, DAILY_RARE_ITEM_BUY_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日稀有道具购买次数已达上限");
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

    private static boolean settleEndedExploresAsChests(long accountId,
                                                       PetDogMapper dogMapper,
                                                       PetItemMapper itemMapper,
                                                       PetTrainingMapper trainingMapper,
                                                       List<PetDogRecord> dogs,
                                                       long now) {
        boolean settled = false;
        for (PetDogRecord dog : dogs) {
            if (!"exploring".equals(dog.getStatus())) {
                continue;
            }
            if (!EXPLORE_LOCATION_BACK_HILL.equals(dog.getExploreLocation())) {
                continue;
            }
            if (dog.getExploreEndsAt() == null || dog.getExploreEndsAt() > now) {
                continue;
            }
            if (itemMapper.addItemIfUnderLimit(accountId, ITEM_BACK_HILL_CHEST, 1,
                    MAX_BACK_HILL_CHEST_COUNT, now) <= 0) {
                throw new IllegalArgumentException(BACK_HILL_CHEST_FULL_ERROR);
            }
            grantFirstExploreFreeLearnIfEligible(trainingMapper, accountId, inferExploreDurationHours(dog), now);
            if (dogMapper.openExplore(dog.getId(), accountId, now) <= 0) {
                throw new IllegalArgumentException("探险结算失败，请刷新后重试");
            }
            settled = true;
        }
        return settled;
    }

    private static int backHillChestCount(PetItemMapper itemMapper, long accountId) {
        PetItemRecord item = itemMapper.findByAccountIdAndItemId(accountId, ITEM_BACK_HILL_CHEST);
        return item == null ? 0 : item.getCount();
    }

    private static void grantFirstExploreFreeLearnIfEligible(PetTrainingMapper mapper, long accountId,
                                                             int durationHours, long now) {
        if (durationHours != EXPLORE_ONE_HOUR) {
            return;
        }
        mapper.ensureFlags(accountId, now);
        mapper.grantFirstExploreFreeLearn(accountId, now);
    }

    private static TrainingSnapshot buildExploreTrainingSnapshot(PetTrainingMapper mapper, long accountId,
                                                                 String skillId) {
        String normalizedSkillId = StrUtil.trim(skillId);
        if (!isTrainingSkillDefined(normalizedSkillId)) {
            return TrainingSnapshot.empty();
        }
        PetTrainingSkillRecord skill = mapper.findSkill(accountId, normalizedSkillId);
        if (skill == null) {
            return TrainingSnapshot.empty();
        }
        return new TrainingSnapshot(skill.getSkillId(), clampTrainingLevel(skill.getLevel()),
                TRAINING_DEFINITION_VERSION);
    }

    private static long exploreDurationMillis(int durationHours, TrainingSnapshot snapshot) {
        long durationMillis = durationHours * 60L * 60L * 1000L;
        int routePercent = snapshot == null || snapshot.level == null
                ? 0
                : trainingEffect(snapshot.skillId, snapshot.level, TRAINING_SKILL_ROUTE);
        if (routePercent <= 0) {
            return durationMillis;
        }
        return (long) Math.ceil(durationMillis * (100 - routePercent) / 100D);
    }

    private static int applyExploreBonesTraining(int baseBones, PetDogRecord dog) {
        int bonusPercent = exploreSnapshotEffect(dog, TRAINING_SKILL_BONES);
        if (bonusPercent <= 0) {
            return baseBones;
        }
        return (int) Math.ceil(baseBones * (100 + bonusPercent) / 100D);
    }

    private static void applyExploreEnergyTraining(PetDogMapper dogMapper, long accountId, PetDogRecord dog,
                                                   int energyLimit, List<PetExploreRewardDTO> rewards, long now) {
        int refundPercent = exploreSnapshotEffect(dog, TRAINING_SKILL_ENERGY);
        if (refundPercent <= 0 || nextExploreRoll() >= refundPercent) {
            return;
        }
        if (dogMapper.addEnergyIfUnderLimit(dog.getId(), accountId, 1, energyLimit, now) > 0) {
            rewards.add(new PetExploreRewardDTO("energy", null, 1));
        }
    }

    private static int exploreSnapshotEffect(PetDogRecord dog, String expectedSkillId) {
        if (dog == null) {
            return 0;
        }
        return trainingEffect(dog.getExploreSkillSnapshotId(),
                dog.getExploreSkillSnapshotLevel() == null ? 0 : dog.getExploreSkillSnapshotLevel(),
                expectedSkillId);
    }

    private static int trainingEffect(String skillId, int level, String expectedSkillId) {
        if (!expectedSkillId.equals(skillId)) {
            return 0;
        }
        List<Integer> effects = TRAINING_SKILL_EFFECTS.get(skillId);
        if (effects == null || effects.isEmpty()) {
            return 0;
        }
        int index = clampTrainingLevel(level) - 1;
        return effects.get(index);
    }

    private static void ensureTrainingSkillExists(String skillId) {
        if (!isTrainingSkillDefined(skillId)) {
            throw new IllegalArgumentException("训练技能不存在");
        }
    }

    private static boolean isTrainingSkillDefined(String skillId) {
        return StrUtil.isNotBlank(skillId) && TRAINING_SKILL_EFFECTS.containsKey(skillId);
    }

    private static int clampTrainingLevel(int level) {
        if (level < 1) {
            return 1;
        }
        return Math.min(level, TRAINING_UPGRADE_COSTS.size());
    }

    private static final class TrainingSnapshot {
        private final String skillId;
        private final Integer level;
        private final String definitionVersion;

        private TrainingSnapshot(String skillId, Integer level, String definitionVersion) {
            this.skillId = skillId;
            this.level = level;
            this.definitionVersion = definitionVersion;
        }

        private static TrainingSnapshot empty() {
            return new TrainingSnapshot(null, null, null);
        }
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

    private static boolean isSupportedExploreLocation(String location) {
        return EXPLORE_LOCATION_BACK_HILL.equals(location)
                || EXPLORE_LOCATION_MYSTERY_CAVE.equals(location);
    }

    private static void ensureMysteryCaveAvailable(SqlSession session, long accountId) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        if (findCollectionCount(collectionMapper, accountId,
                MYSTERY_CAVE_COMPLETED_COLLECTION_ID) > 0) {
            throw new IllegalArgumentException("神秘洞穴已经完成");
        }
        if (findCollectionCount(collectionMapper, accountId,
                TREASURE_MAP_FRAGMENT_COLLECTION_ID) < HUSKY_TREASURE_MAP_FRAGMENT_LIMIT) {
            throw new IllegalArgumentException("集齐 3 张藏宝图碎片后才能进入神秘洞穴");
        }
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
                                          PetDogRecord dog,
                                          List<PetExploreRewardDTO> rewards, String today, long now) {
        PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        Map<String, Integer> itemCounts = exploreItemCounts(itemMapper, accountId);
        int collectionBonus = exploreSnapshotEffect(dog, TRAINING_SKILL_COLLECTION);
        int treasureBonus = exploreSnapshotEffect(dog, TRAINING_SKILL_TREASURE);
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
                applyExploreEasterEventReward(session, accountId, assetsMapper, rewards, now);
            } else if (roll < 80 + collectionBonus) {
                applyExploreCollectionReward(session, accountId, rewards, now);
            } else if (roll < 80 + collectionBonus + treasureBonus) {
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

    private static void applyExploreEasterEventReward(SqlSession session, long accountId,
                                                      PetAssetsMapper assetsMapper,
                                                      List<PetExploreRewardDTO> rewards, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        List<String> eventIds = availableExploreEasterEventIds(collectionMapper, accountId);
        if (eventIds.isEmpty()) {
            addExploreBones(assetsMapper, accountId, EXPLORE_EASTER_EVENT_OVERFLOW_BONES, rewards, now);
            return;
        }

        String eventId = eventIds.get(Math.floorMod(nextExploreEasterEventIndex(), eventIds.size()));
        collectionMapper.addCollection(accountId, eventId, now);
        rewards.add(new PetExploreRewardDTO("collection", eventId, 1));
    }

    private static List<String> availableExploreEasterEventIds(PetCollectionMapper collectionMapper, long accountId) {
        List<String> eventIds = new ArrayList<>();
        if (!isCollectionDiscovered(collectionMapper, accountId, EASTER_NEIGHBOR_SLIPPER_COLLECTION_ID)) {
            eventIds.add(EASTER_NEIGHBOR_SLIPPER_COLLECTION_ID);
        }
        if (findCollectionCount(collectionMapper, accountId,
                TREASURE_MAP_FRAGMENT_COLLECTION_ID) < HUSKY_TREASURE_MAP_FRAGMENT_LIMIT) {
            eventIds.add(TREASURE_MAP_FRAGMENT_COLLECTION_ID);
        }
        if (!isCollectionDiscovered(collectionMapper, accountId, EASTER_SNAIL_COLLECTION_ID)) {
            eventIds.add(EASTER_SNAIL_COLLECTION_ID);
        }
        return eventIds;
    }

    private static boolean isCollectionDiscovered(PetCollectionMapper collectionMapper, long accountId, String itemId) {
        return collectionMapper.countDiscovered(accountId, itemId) > 0;
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

    private static int nextExploreEasterEventIndex() {
        return exploreEasterEventSupplier.getAsInt();
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

    private static String dailyRareShopItemId(LocalDate date) {
        int itemIndex = (int) Math.floorMod(date.toEpochDay(), LUCKY_BAG_RARE_ITEM_IDS.size());
        return LUCKY_BAG_RARE_ITEM_IDS.get(itemIndex);
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
        PetCheckinMilestoneRewardDTO milestoneReward;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
            if (checkinMapper.findByAccountIdAndDate(accountId, today) != null) {
                throw new IllegalArgumentException("今天已经签到过了");
            }

            int previousTotalCheckins = checkinMapper.countByAccountId(accountId);
            int cycleDay = previousTotalCheckins % 7 + 1;
            ensureAssets(session, accountId);
            checkinMapper.insert(PetCheckinRecord.builder()
                    .accountId(accountId)
                    .checkinDate(today)
                    .cycleDay(cycleDay)
                    .createdAt(now)
                    .build());
            applyCheckinReward(session, accountId, cycleDay, now, true);
            milestoneReward = applyCheckinMilestoneReward(session, accountId, previousTotalCheckins + 1, now);
            session.commit();
        }

        return profileLocked(accountId, milestoneReward);
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
        PetCheckinMilestoneRewardDTO milestoneReward;
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

            int previousTotalCheckins = checkinMapper.countByAccountId(accountId);
            int cycleDay = previousTotalCheckins % 7 + 1;
            checkinMapper.insert(PetCheckinRecord.builder()
                    .accountId(accountId)
                    .checkinDate(checkinDateText)
                    .cycleDay(cycleDay)
                    .createdAt(now)
                    .build());
            applyCheckinReward(session, accountId, cycleDay, now, false);
            milestoneReward = applyCheckinMilestoneReward(session, accountId, previousTotalCheckins + 1, now);
            session.commit();
        }

        return profileLocked(accountId, milestoneReward);
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

    private static void applyCheckinReward(SqlSession session, long accountId, int cycleDay, long now,
                                           boolean actualCheckin) {
        PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
        boolean rewardApplied = true;
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
                rewardApplied = false;
                break;
        }
        if (rewardApplied && hasCompletedBackHillCollectionSet(session.getMapper(PetCollectionMapper.class), accountId)) {
            mapper.addBones(accountId, BACK_HILL_COLLECTION_CHECKIN_BONUS_BONES, now);
        }
        if (rewardApplied && actualCheckin) {
            applyNeighborSlipperCheckinBonus(session, accountId, now);
        }
    }

    private static void applyNeighborSlipperCheckinBonus(SqlSession session, long accountId, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        if (findCollectionCount(collectionMapper, accountId, EASTER_NEIGHBOR_SLIPPER_COLLECTION_ID) <= 0) {
            return;
        }
        if (collectionMapper.decrementCollectionIfEnough(accountId,
                EASTER_NEIGHBOR_SLIPPER_COLLECTION_ID, 1, now) > 0) {
            session.getMapper(PetAssetsMapper.class).addBones(
                    accountId, EASTER_NEIGHBOR_SLIPPER_CHECKIN_BONES, now);
        }
    }

    private static PetCheckinMilestoneRewardDTO applyCheckinMilestoneReward(SqlSession session, long accountId,
                                                                            int totalCheckins, long now) {
        if (totalCheckins <= 0 || totalCheckins % CHECKIN_MILESTONE_INTERVAL != 0) {
            return null;
        }

        int milestoneIndex = totalCheckins / CHECKIN_MILESTONE_INTERVAL;
        String decorationId = CHECKIN_MILESTONE_DECORATION_IDS.get(
                Math.floorMod(milestoneIndex - 1, CHECKIN_MILESTONE_DECORATION_IDS.size()));
        session.getMapper(PetCollectionMapper.class).addCollection(accountId, decorationId, now);

        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        String itemId = pickAvailableLuckyBagItem(LUCKY_BAG_RARE_ITEM_IDS, luckyBagItemCounts(itemMapper, accountId));
        int overflowBones = 0;
        if (itemId == null || itemMapper.addItemIfUnderLimit(accountId, itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
            itemId = null;
            overflowBones = CHECKIN_MILESTONE_RARE_ITEM_OVERFLOW_BONES;
            session.getMapper(PetAssetsMapper.class).addBones(accountId, overflowBones, now);
        }

        return new PetCheckinMilestoneRewardDTO(milestoneIndex, decorationId, itemId, overflowBones);
    }

    private static boolean hasCompletedBackHillCollectionSet(PetCollectionMapper collectionMapper, long accountId) {
        return hasCompletedCollectionSet(collectionMapper, accountId, BACK_HILL_COLLECTION_ITEM_IDS);
    }

    private static int effectiveEnergyLimit(PetCollectionMapper collectionMapper, long accountId, int baseEnergyLimit) {
        int energyLimit = Math.max(0, baseEnergyLimit);
        if (hasCompletedCollectionSet(collectionMapper, accountId, SNOW_MOUNTAIN_COLLECTION_ITEM_IDS)) {
            energyLimit += SNOW_MOUNTAIN_COLLECTION_ENERGY_LIMIT_BONUS;
        }
        return energyLimit;
    }

    private static int effectiveEnergyLimit(SqlSession session, long accountId, int baseEnergyLimit) {
        return effectiveEnergyLimit(session.getMapper(PetCollectionMapper.class), accountId, baseEnergyLimit);
    }

    private static int effectiveExploreBaseBones(PetCollectionMapper collectionMapper, long accountId, int baseBones) {
        if (!hasCompletedCollectionSet(collectionMapper, accountId, OLD_LIBRARY_COLLECTION_ITEM_IDS)) {
            return baseBones;
        }
        return (int) Math.ceil(baseBones * (100 + OLD_LIBRARY_COLLECTION_BASE_BONES_BONUS_PERCENT) / 100D);
    }

    private static boolean hasCompletedCollectionSet(PetCollectionMapper collectionMapper, long accountId,
                                                     List<String> collectionItemIds) {
        Set<String> discoveredItems = new HashSet<>();
        for (PetCollectionRecord collection : collectionMapper.listByAccountId(accountId)) {
            discoveredItems.add(collection.getItemId());
        }
        return discoveredItems.containsAll(collectionItemIds);
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

    private static int checkinMilestoneRemaining(int totalCheckins) {
        int normalizedTotal = Math.max(0, totalCheckins);
        int remainder = normalizedTotal % CHECKIN_MILESTONE_INTERVAL;
        return remainder == 0 ? CHECKIN_MILESTONE_INTERVAL : CHECKIN_MILESTONE_INTERVAL - remainder;
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

    private static int grantDailyDogBond(PetDailyCounterMapper counterMapper,
                                         long accountId,
                                         String today,
                                         PetDogRecord dog,
                                         String sourceCounterPrefix,
                                         long now) {
        String totalCounter = DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX + dog.getId();
        if (findDailyCounterValue(counterMapper, accountId, today, totalCounter) >= DAILY_DOG_BOND_LIMIT) {
            return dog.getBond();
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today,
                sourceCounterPrefix + dog.getId(), 1, now) <= 0) {
            return dog.getBond();
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today,
                totalCounter, DAILY_DOG_BOND_LIMIT, now) <= 0) {
            return dog.getBond();
        }
        return clampDogStat(dog.getBond() + 1);
    }

    private static PetInteractionStatusDTO buildInteractionStatus(PetDailyCounterMapper mapper, long accountId,
                                                                  String date) {
        int dailyBonusUsed = findDailyCounterValue(mapper, accountId, date, DAILY_COUNTER_INTERACTION_BONUS_BONES);
        Map<String, Integer> itemRewardCounts = new HashMap<>();
        Map<String, Integer> itemRemainingRewardCounts = new HashMap<>();
        for (String itemId : INTERACTION_ITEM_REWARD_BONES.keySet()) {
            int used = findDailyCounterValue(mapper, accountId, date,
                    DAILY_COUNTER_INTERACTION_ITEM_PREFIX + itemId);
            itemRewardCounts.put(itemId, used);
            itemRemainingRewardCounts.put(itemId, Math.max(0, INTERACTION_ITEM_DAILY_REWARD_LIMIT - used));
        }
        return new PetInteractionStatusDTO(
                INTERACTION_DAILY_BONUS_CAP,
                dailyBonusUsed,
                Math.max(0, INTERACTION_DAILY_BONUS_CAP - dailyBonusUsed),
                INTERACTION_ITEM_DAILY_REWARD_LIMIT,
                itemRewardCounts,
                itemRemainingRewardCounts);
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
                clampDogStat(row.getSpeed()), clampDogStat(row.getStamina()), clampDogStat(row.getBurst()),
                clampDogStat(row.getWisdom()), clampDogStat(row.getBond()),
                row.getEnergy(), row.getStatus(), row.getExploreLocation(), row.getExploreEndsAt(),
                row.getExploreSkillId(), row.getExploreSkillSnapshotId(), row.getExploreSkillSnapshotLevel(),
                row.getExploreSkillSnapshotVersion(),
                row.getRaceCount(), row.getRaceFirstCount(), row.getWeeklyPoints());
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
        int totalStats = clampDogStat(dog.getSpeed()) + clampDogStat(dog.getStamina())
                + clampDogStat(dog.getBurst()) + clampDogStat(dog.getWisdom()) + clampDogStat(dog.getBond());
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

    private static int clampDogStat(int value) {
        return Math.max(DOG_STAT_MIN, Math.min(DOG_STAT_MAX, value));
    }

    private static PetAssetsDTO toDTO(PetAssetsRecord row) {
        return toDTO(row, row.getEnergyLimit());
    }

    private static PetAssetsDTO toDTO(PetAssetsRecord row, int energyLimit) {
        return new PetAssetsDTO(row.getBones(), row.getFood(), row.getMakeupCards(), row.getDogSlots(),
                energyLimit);
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
            if ("shiba".equals(breed)) {
                return new BreedStats(true, 10, 10, 15, 8, 7);
            }
            if ("husky".equals(breed)) {
                return new BreedStats(true, 12, 12, 8, 5, 13);
            }
            return null;
        }
    }

}
