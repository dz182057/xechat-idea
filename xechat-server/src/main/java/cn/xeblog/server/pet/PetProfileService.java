package cn.xeblog.server.pet;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetArcadeStatusDTO;
import cn.xeblog.commons.entity.pet.PetCheckinMilestoneRewardDTO;
import cn.xeblog.commons.entity.pet.PetAssetsDTO;
import cn.xeblog.commons.entity.pet.PetCheckinStatusDTO;
import cn.xeblog.commons.entity.pet.PetCollectionItemDTO;
import cn.xeblog.commons.entity.pet.PetDailyCompanionDogStatusDTO;
import cn.xeblog.commons.entity.pet.PetDailyCompanionStatusDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetExploreChestDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenResultDTO;
import cn.xeblog.commons.entity.pet.PetExploreRewardDTO;
import cn.xeblog.commons.entity.pet.PetExploreStartDTO;
import cn.xeblog.commons.entity.pet.PetExploreStatusDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetFlip7ActionResultDTO;
import cn.xeblog.commons.entity.pet.PetFlip7CardDTO;
import cn.xeblog.commons.entity.pet.PetFlip7CardCountDTO;
import cn.xeblog.commons.entity.pet.PetFlip7RoundDTO;
import cn.xeblog.commons.entity.pet.PetFlip7StatusDTO;
import cn.xeblog.commons.entity.pet.PetInventoryItemDTO;
import cn.xeblog.commons.entity.pet.PetInteractionStatusDTO;
import cn.xeblog.commons.entity.pet.PetMakeupCheckinDTO;
import cn.xeblog.commons.entity.pet.PetPendingOldTennisBallDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetResolveOldTennisBallDTO;
import cn.xeblog.commons.entity.pet.PetRenameDTO;
import cn.xeblog.commons.entity.pet.PetSellItemDTO;
import cn.xeblog.commons.entity.pet.PetSetCompanionDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetShopStatusDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillActionDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillDefinitionDTO;
import cn.xeblog.commons.entity.pet.PetTrainingStatusDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntExtraRewardDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntProbabilityDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntRedeemSkinDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntSpinResultDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntStatusDTO;
import cn.xeblog.commons.entity.pet.PetUseItemDTO;
import cn.xeblog.commons.entity.pet.PetWalkDogDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.mapper.AccountMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 狗狗宇宙个人资料服务。
 */
public final class PetProfileService {

    private static final int DEFAULT_BONES = 300;
    private static final int DEFAULT_FOOD = 6;
    private static final int DEFAULT_MAKEUP_CARDS = 0;
    private static final int DEFAULT_DOG_SLOTS = 1;
    private static final int MAX_DOG_SLOTS = 2;
    private static final int SECOND_DOG_SLOT_PRICE = 3000;
    private static final int PUBLIC_DOG_ADOPTION_PRICE = 750;
    private static final int DEFAULT_ENERGY_LIMIT = 10;
    private static final int DAILY_FEED_LIMIT = 5;
    private static final int DAILY_DOG_BOND_LIMIT = 4;
    private static final String DOG_STAGE_PUPPY = "puppy";
    private static final String DOG_STAGE_ADULT = "adult";
    private static final String DOG_STAGE_CHAMPION = "champion";
    private static final int DOG_STAT_MIN = 0;
    private static final int DOG_STAT_MAX = 100;
    private static final int DOG_ADULT_BOND_THRESHOLD = 40;
    private static final int DOG_ADULT_GAME_WIN_THRESHOLD = 3;
    private static final int DOG_CHAMPION_BOND_THRESHOLD = 80;
    private static final int DOG_CHAMPION_GAME_WIN_THRESHOLD = 1;
    private static final int TACIT_QUIZ_SAME_ANSWERS_PER_GROWTH_WIN = 5;
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
    private static final int SHOP_DAILY_SKIN_ITEM_PRICE = 1680;
    private static final int SHOP_LUCKY_BAG_PRICE = 250;
    private static final int TREASURE_HUNT_DAILY_FREE_LIMIT = 1;
    private static final int TREASURE_HUNT_PAID_COST = 50;
    private static final int TREASURE_HUNT_SKIN_FRAGMENTS_PER_SKIN = 10;
    private static final int TREASURE_HUNT_ROLL_SCALE = 100_000;
    private static final int TREASURE_HUNT_SKIN_FRAGMENT_MAX_GRANT = 10_000;
    private static final int FLIP7_DAILY_FREE_LIMIT = 1;
    private static final int FLIP7_PAID_COST = 50;
    private static final int FLIP7_UNIQUE_TARGET = 7;
    private static final int FLIP7_BONUS = 15;
    private static final int FLIP7_FLIP_THREE_COUNT = 3;
    private static final String FLIP7_STATE_ACTIVE = "active";
    private static final String FLIP7_STATE_STOOD = "stood";
    private static final String FLIP7_STATE_BUST = "bust";
    private static final String FLIP7_STATE_FROZEN = "frozen";
    private static final String FLIP7_STATE_FLIP7 = "flip7";
    private static final String FLIP7_STATE_DECK_EMPTY = "deck_empty";
    private static final String FLIP7_EVENT_STARTED = "started";
    private static final String FLIP7_EVENT_RESUMED = "resumed";
    private static final String FLIP7_EVENT_DRAWN = "drawn";
    private static final String FLIP7_EVENT_SETTLED = "settled";
    private static final String FLIP7_EVENT_STOOD = "stood";
    private static final String FLIP7_CARD_NUMBER_PREFIX = "N:";
    private static final String FLIP7_CARD_MODIFIER_PREFIX = "M:";
    private static final String FLIP7_CARD_ACTION_PREFIX = "A:";
    private static final long SHOP_SHELF_REFRESH_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final int SHOP_SHELF_NORMAL_ITEM_COUNT = 3;
    private static final int SHOP_SHELF_MAX_PAID_REFRESHES = 3;
    private static final List<Integer> SHOP_SHELF_PAID_REFRESH_COSTS =
            Collections.unmodifiableList(Arrays.asList(30, 50, 70));
    private static final int MAX_FOOD = 99;
    private static final int MAX_MAKEUP_CARDS = 3;
    private static final int MAX_ITEM_COUNT = 9;
    private static final int MONTHLY_MAKEUP_CARD_BUY_LIMIT = 2;
    private static final int DAILY_NORMAL_ITEM_BUY_LIMIT = 3;
    private static final int DAILY_RARE_ITEM_BUY_LIMIT = 1;
    private static final int DAILY_SKIN_ITEM_BUY_LIMIT = 1;
    private static final int DAILY_LUCKY_BAG_BUY_LIMIT = 2;
    private static final int SEVENTH_DAY_CHECKIN_BONES = 100;
    private static final int SEVENTH_DAY_CHECKIN_NORMAL_ITEM_COUNT = 1;
    private static final int CHECKIN_ITEM_OVERFLOW_BONES = 10;
    private static final int CHECKIN_MILESTONE_INTERVAL = 28;
    private static final int CHECKIN_MILESTONE_EPIC_ITEM_OVERFLOW_BONES = 80;
    private static final int SHIBA_CHECKIN_PITY_COUNT = 30;
    private static final int SHIBA_DAILY_CHECKIN_ROLL_THRESHOLD = 3;
    private static final String SHIBA_UNLOCK_COLLECTION_ID = "breed_shiba_unlocked";
    private static final int BACK_HILL_COLLECTION_CHECKIN_BONUS_BONES = 5;
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
    private static final String EXPLORE_LOCATION_CREEK = "creek";
    private static final String EXPLORE_LOCATION_CONSTRUCTION_SITE = "construction_site";
    private static final String EXPLORE_LOCATION_OLD_LIBRARY = "old_library";
    private static final String EXPLORE_LOCATION_SNOW_MOUNTAIN = "snow_mountain";
    private static final String EXPLORE_LOCATION_MYSTERY_CAVE = "mystery_cave";
    private static final String ITEM_BACK_HILL_CHEST = "chest_back_hill";
    private static final String ITEM_CREEK_CHEST = "chest_creek";
    private static final String ITEM_CONSTRUCTION_SITE_CHEST = "chest_construction_site";
    private static final String ITEM_OLD_LIBRARY_CHEST = "chest_old_library";
    private static final String ITEM_SNOW_MOUNTAIN_CHEST = "chest_snow_mountain";
    private static final int MAX_EXPLORE_CHEST_COUNT = 99;
    private static final String EXPLORE_CHEST_STATUS_AVAILABLE = "available";
    private static final String ITEM_LEDGER_GAIN = "gain";
    private static final String ITEM_LEDGER_SPEND = "spend";
    private static final String ITEM_LEDGER_SOURCE_EXPLORE_RETURN_CHEST = "explore_return_chest";
    private static final String ITEM_LEDGER_SOURCE_OPEN_EXPLORE_CHEST = "open_explore_chest";
    private static final String ITEM_LEDGER_SOURCE_TREASURE_HUNT = "treasure_hunt";
    private static final String ITEM_LEDGER_SOURCE_TREASURE_HUNT_REDEEM_SKIN = "treasure_hunt_redeem_skin";
    private static final String ITEM_LEDGER_SOURCE_SHOP_BUY_NORMAL = "shop_buy_normal";
    private static final String ITEM_LEDGER_SOURCE_SHOP_BUY_DAILY_RARE = "shop_buy_daily_rare";
    private static final String ITEM_LEDGER_SOURCE_SHOP_BUY_DAILY_SKIN = "shop_buy_daily_skin";
    private static final String ITEM_LEDGER_SOURCE_SHOP_BUY_LUCKY_BAG = "shop_buy_lucky_bag";
    private static final String ITEM_LEDGER_SOURCE_EXPLORE_REWARD = "explore_reward";
    private static final String ITEM_LEDGER_SOURCE_LEGACY_CHEST_MIGRATION = "legacy_chest_migration";
    private static final String ITEM_LEDGER_SOURCE_USE_ITEM = "use_item";
    private static final String ITEM_LEDGER_SOURCE_SELL_ITEM = "sell_item";
    private static final String ITEM_LEDGER_SOURCE_CHECKIN_REWARD = "checkin_reward";
    private static final String BACK_HILL_CHEST_FULL_ERROR = "后山箱子已达上限，打开后再探险";
    private static final String CREEK_CHEST_FULL_ERROR = "小溪箱子已达上限，打开后再探险";
    private static final String CONSTRUCTION_SITE_CHEST_FULL_ERROR = "工地箱子已达上限，打开后再探险";
    private static final String OLD_LIBRARY_CHEST_FULL_ERROR = "旧书馆箱子已达上限，打开后再探险";
    private static final String SNOW_MOUNTAIN_CHEST_FULL_ERROR = "雪山箱子已达上限，打开后再探险";
    private static final String COUNTER_DATE_LIFETIME = "lifetime";
    private static final String COUNTER_EXPLORE_COMPLETE_PREFIX = "explore_complete_";
    private static final String COUNTER_MINI_GAME_WIN_PREFIX = "mini_game_win_";
    private static final String COUNTER_TACIT_QUIZ_SAME_ANSWERS = "mini_game_tacit_quiz_same_answers";
    private static final int CREEK_UNLOCK_DRAW_GUESS_WINS = 10;
    private static final int CREEK_UNLOCK_TACIT_QUIZ_SAME_ANSWERS = 50;
    private static final int CONSTRUCTION_SITE_UNLOCK_QUICK_QUIZ_WINS = 10;
    private static final int OLD_LIBRARY_UNLOCK_LIBRARY_WINS = 10;
    private static final int SNOW_MOUNTAIN_UNLOCK_OLD_LIBRARY_COMPLETIONS = 3;
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
    private static final String EASTER_VISIT_DOG_TAG_COLLECTION_ID = "easter_visit_dog_tag";
    private static final String EASTER_OLD_TENNIS_COLLECTION_ID = "easter_old_tennis";
    private static final String EASTER_OLD_TENNIS_PENDING_PREFIX = "old_tennis_pending:";
    private static final String DAILY_COUNTER_OLD_TENNIS_BOND_PREFIX = "bond_old_tennis:";
    private static final int EASTER_OLD_TENNIS_RETURN_BOND = 2;
    private static final int EASTER_NEIGHBOR_SLIPPER_CHECKIN_BONES = 50;
    private static final int EASTER_VISIT_DOG_TAG_BONES = 10;
    private static final Set<Game> MINI_GAME_ROOM_BONUS_GAMES = EnumSet.of(
            Game.GOBANG,
            Game.MINESWEEPER,
            Game.DRAW_GUESS,
            Game.TACIT_QUIZ,
            Game.QUICK_QUIZ,
            Game.TURTLE_SOUP
    );
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
    private static final String DAILY_COUNTER_SHOP_DAILY_SKIN_ITEM_BUY = "shop_daily_skin_item_buy";
    private static final String DAILY_COUNTER_SHOP_LUCKY_BAG_BUY = "shop_lucky_bag_buy";
    private static final String COUNTER_SHOP_SHELF_PAID_REFRESH = "shop_shelf_paid_refresh";
    private static final String DAILY_COUNTER_TREASURE_HUNT_FREE_USED = "treasure_hunt_free_used";
    private static final String COUNTER_TREASURE_HUNT_BONUS_SPINS = "treasure_hunt_bonus_spins";
    private static final String DAILY_COUNTER_FLIP7_FREE_USED = "flip7_free_used";
    private static final String DAILY_COUNTER_EXPLORE_START = "explore_start";
    private static final String DAILY_COUNTER_EXPLORE_ITEM_GAIN = "explore_item_gain";
    private static final String DAILY_COUNTER_MINI_GAME_COMPLETE = "mini_game_complete";
    private static final String DAILY_COUNTER_MINI_GAME_WIN = "mini_game_win";
    private static final String DAILY_COUNTER_MINI_GAME_FIRST_WIN = "mini_game_first_win";
    private static final String DAILY_COUNTER_INTERACTION_BONUS_BONES = "interaction_bonus_bones";
    private static final String DAILY_COUNTER_INTERACTION_ITEM_PREFIX = "interaction_item_";
    private static final String MONTHLY_COUNTER_SHOP_MAKEUP_CARD_BUY = "shop_makeup_card_buy";
    private static final Map<String, Integer> INTERACTION_ITEM_REWARD_BONES =
            PetItemDefinitions.interactionRewardBones();
    private static final List<String> LUCKY_BAG_NORMAL_ITEM_IDS = PetItemDefinitions.luckyBagNormalItemIds();
    private static final List<String> LUCKY_BAG_RARE_ITEM_IDS = PetItemDefinitions.luckyBagRareItemIds();
    private static final List<String> LUCKY_BAG_EPIC_ITEM_IDS = PetItemDefinitions.luckyBagEpicItemIds();
    private static final List<String> SKIN_ITEM_IDS = PetItemDefinitions.skinItemIds();
    private static final List<String> DAILY_SKIN_SHOP_ITEM_IDS = PetItemDefinitions.dailySkinShopItemIds();
    private static final List<String> BACK_HILL_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_mine_mark",
            "item_mine_safe_ping"
    ));
    private static final List<String> BACK_HILL_RARE_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_mine_shield",
            "item_mine_detector",
            "item_mine_counter"
    ));
    private static final List<String> CREEK_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_draw_advance_hint",
            "item_draw_pattern",
            "item_draw_overlap",
            "item_sync_prophecy"
    ));
    private static final List<String> CREEK_RARE_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_draw_reveal_char",
            "item_sync_perspective"
    ));
    private static final List<String> CONSTRUCTION_SITE_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_quiz_score_pad",
            "item_quiz_duel"
    ));
    private static final List<String> CONSTRUCTION_SITE_RARE_ITEM_IDS =
            Collections.unmodifiableList(Collections.singletonList("item_quiz_wrong_option"));
    private static final Set<String> CONSTRUCTION_SITE_LUCKY_BAG_ITEM_IDS =
            Collections.unmodifiableSet(createConstructionSiteLuckyBagItemIds());
    private static final List<String> OLD_LIBRARY_NORMAL_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_gomoku_prediction",
            "item_prophecy"
    ));
    private static final List<String> OLD_LIBRARY_RARE_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "item_gomoku_guard",
            "item_gomoku_finisher",
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
    private static final List<String> CONSTRUCTION_SITE_COLLECTION_ITEM_IDS = Collections.unmodifiableList(Arrays.asList(
            "construction_site_helmet",
            "construction_site_gear",
            "construction_site_nut",
            "construction_site_brick",
            "construction_site_driver",
            "construction_site_clip"
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

    private static Set<String> createConstructionSiteLuckyBagItemIds() {
        Set<String> itemIds = new HashSet<>();
        itemIds.addAll(CONSTRUCTION_SITE_NORMAL_ITEM_IDS);
        itemIds.addAll(CONSTRUCTION_SITE_RARE_ITEM_IDS);
        return itemIds;
    }

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

    private static final List<String> LUCKY_BAG_ITEM_IDS = PetItemDefinitions.luckyBagAllItemIds();
    private static final Set<String> SHOP_NORMAL_ITEM_IDS = PetItemDefinitions.shopNormalItemIds();
    private static final Map<String, Integer> SELL_ITEM_PRICES = PetItemDefinitions.sellItemPrices();
    private static final List<String> TREASURE_HUNT_NORMAL_ITEM_IDS = PetItemDefinitions.luckyBagNormalItemIds();
    private static final List<String> TREASURE_HUNT_RARE_ITEM_IDS = PetItemDefinitions.luckyBagRareItemIds();
    private static final List<String> TREASURE_HUNT_EPIC_ITEM_IDS = PetItemDefinitions.luckyBagEpicItemIds();
    private static final List<String> TREASURE_HUNT_RARE_SKIN_ITEM_IDS = PetItemDefinitions.rareSkinItemIds();
    private static final List<String> TREASURE_HUNT_EPIC_SKIN_ITEM_IDS = PetItemDefinitions.epicSkinItemIds();
    private static final List<String> TREASURE_HUNT_LEGENDARY_SKIN_ITEM_IDS =
            PetItemDefinitions.legendarySkinItemIds();
    private static final int TREASURE_HUNT_SLOT_COUNT = 3;
    private static final List<TreasureSlotOption> TREASURE_HUNT_SLOT_OPTIONS =
            Collections.unmodifiableList(Arrays.asList(
                    TreasureSlotOption.bones("bone_5", "骨头币 5", 45_316, 5),
                    TreasureSlotOption.bones("bone_10", "骨头币 10", 29_336, 10),
                    TreasureSlotOption.bones("bone_15", "骨头币 15", 10_131, 15),
                    TreasureSlotOption.bones("bone_20", "骨头币 20", 4_956, 20),
                    TreasureSlotOption.bones("bone_50", "骨头币 50", 759, 50),
                    TreasureSlotOption.bones("bone_100", "骨头币 100", 1_300, 100),
                    TreasureSlotOption.prize("item_normal", "normal_item", "普通道具", 4_500, 1),
                    TreasureSlotOption.prize("item_rare", "rare_item", "稀有道具", 500, 1),
                    TreasureSlotOption.prize("item_epic", "epic_item", "史诗道具", 40, 1),
                    TreasureSlotOption.prize("rare_skin_fragment", "rare_skin_fragment", "稀有皮肤碎片", 2_500, 1),
                    TreasureSlotOption.prize("epic_skin_fragment", "epic_skin_fragment", "史诗皮肤碎片", 600, 1),
                    TreasureSlotOption.prize("rare_skin", "rare_skin", "完整稀有皮肤", 50, 1),
                    TreasureSlotOption.prize("epic_skin", "epic_skin", "完整史诗皮肤", 10, 1),
                    TreasureSlotOption.prize("legend_skin", "legend_skin", "完整传说皮肤", 2, 1)
            ));
    private static final Map<Long, Object> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
    private static IntSupplier exploreRollSupplier = () -> ThreadLocalRandom.current().nextInt(100);
    private static IntSupplier exploreEasterEventSupplier = () -> ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    private static IntSupplier luckyBagRarityRollSupplier = () -> ThreadLocalRandom.current().nextInt(100);
    private static IntSupplier luckyBagItemIndexSupplier = () -> ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    private static IntSupplier shibaCheckinRollSupplier = () -> ThreadLocalRandom.current().nextInt(100);
    private static IntSupplier treasureHuntSlotRollSupplier =
            () -> ThreadLocalRandom.current().nextInt(TREASURE_HUNT_ROLL_SCALE);
    private static IntSupplier treasureHuntItemIndexSupplier = () -> ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    private static Supplier<List<Flip7Card>> flip7DeckSupplier = PetProfileService::createShuffledFlip7Deck;

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

    public static int itemCarrySlotLimit(long accountId) {
        if (accountId <= 0L) {
            return DEFAULT_DOG_SLOTS;
        }
        synchronized (accountLock(accountId)) {
            try (SqlSession session = DbInitializer.factory().openSession(true)) {
                PetAssetsRecord assets = findAssetsOrDefault(session, accountId);
                return Math.min(MAX_DOG_SLOTS, Math.max(DEFAULT_DOG_SLOTS, assets.getDogSlots()));
            }
        }
    }

    static PetProfileDTO profileLocked(long accountId) {
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
            boolean energyRefreshed = refreshExpiredAccountEnergy(session.getMapper(PetAssetsMapper.class), accountId,
                    energyLimit, todayText, now);
            if (energyRefreshed) {
                assets = session.getMapper(PetAssetsMapper.class).findByAccountId(accountId);
            }
            List<PetDogRecord> rows = dogMapper.listByOwner(accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetExploreChestMapper chestMapper = session.getMapper(PetExploreChestMapper.class);
            PetItemLedgerMapper ledgerMapper = session.getMapper(PetItemLedgerMapper.class);
            PetTrainingMapper trainingMapper = session.getMapper(PetTrainingMapper.class);
            PetDailyCounterMapper dailyCounterMapper = session.getMapper(PetDailyCounterMapper.class);
            Flip7GameState flip7State = ensureFlip7State(session.getMapper(PetFlip7StateMapper.class),
                    accountId, todayText, now);
            boolean legacyChestMigrated = migrateLegacyExploreChests(accountId, itemMapper, chestMapper,
                    ledgerMapper, now);
            boolean exploreSettled = settleEndedExploresAsChests(accountId, dogMapper, chestMapper,
                    ledgerMapper, trainingMapper, dailyCounterMapper, rows, now);
            if (exploreSettled) {
                rows = dogMapper.listByOwner(accountId);
            }
            boolean dogStageChanged = updateDogGrowthStages(dogMapper, dailyCounterMapper, accountId, rows, now);
            List<PetItemRecord> itemRows = itemMapper.listPositiveByAccountId(accountId);
            List<PetExploreChestRecord> chestRows = chestMapper.listAvailableByAccountId(accountId);
            List<PetCollectionRecord> collectionRows = collectionMapper.listByAccountId(accountId);
            PetCheckinMapper checkinMapper = session.getMapper(PetCheckinMapper.class);
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
            profile.setDiscoveredItemIds(ledgerMapper.listGainedItemIds(accountId));
            List<PetExploreChestDTO> exploreChests = new ArrayList<>(chestRows.size());
            for (PetExploreChestRecord row : chestRows) {
                exploreChests.add(toDTO(row));
            }
            profile.setExploreChests(exploreChests);
            List<PetCollectionItemDTO> collections = new ArrayList<>(collectionRows.size());
            for (PetCollectionRecord row : collectionRows) {
                collections.add(toDTO(row));
            }
            profile.setCollections(collections);
            List<String> activeDogIds = resolveActiveDogIds(assets.getCompanionDogId(), dogs);
            profile.setActiveDogIds(activeDogIds);
            profile.setCompanionDogId(activeDogIds.isEmpty() ? null : activeDogIds.get(0));
            profile.setCheckinStatus(new PetCheckinStatusDTO(todayText, todayCheckin != null, cycleDay,
                    totalCheckins, checkinMilestoneRemaining(totalCheckins),
                    accountCheckinStartDate(session, accountId, assets, todayText),
                    checkedDatesInMonth, lastMilestoneReward));
            int treasureMapFragments = Math.min(HUSKY_TREASURE_MAP_FRAGMENT_LIMIT,
                    findCollectionCount(collectionMapper, accountId, TREASURE_MAP_FRAGMENT_COLLECTION_ID));
            boolean mysteryCaveCompleted = findCollectionCount(collectionMapper, accountId,
                    MYSTERY_CAVE_COMPLETED_COLLECTION_ID) > 0;
            int backHillCompletions = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    exploreCompleteCounter(EXPLORE_LOCATION_BACK_HILL));
            int creekCompletions = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    exploreCompleteCounter(EXPLORE_LOCATION_CREEK));
            int oldLibraryCompletions = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    exploreCompleteCounter(EXPLORE_LOCATION_OLD_LIBRARY));
            int minesweeperWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.MINESWEEPER));
            int drawGuessWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.DRAW_GUESS));
            int tacitQuizWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.TACIT_QUIZ));
            int tacitQuizSameAnswers = findTacitQuizSameAnswers(dailyCounterMapper, accountId, tacitQuizWins);
            int quickQuizWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.QUICK_QUIZ));
            int gobangWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.GOBANG));
            int turtleSoupWins = findLifetimeCounterValue(dailyCounterMapper, accountId,
                    miniGameWinCounter(Game.TURTLE_SOUP));
            int oldLibraryWins = gobangWins + turtleSoupWins;
            profile.setExploreStatus(new PetExploreStatusDTO(
                    DAILY_EXPLORE_START_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_START),
                    DAILY_EXPLORE_ITEM_GAIN_LIMIT,
                    findDailyCounterValue(dailyCounterMapper, accountId, todayText, DAILY_COUNTER_EXPLORE_ITEM_GAIN),
                    treasureMapFragments,
                    treasureMapFragments >= HUSKY_TREASURE_MAP_FRAGMENT_LIMIT,
                    mysteryCaveCompleted,
                    mysteryCaveCompleted,
                    backHillCompletions,
                    creekCompletions,
                    minesweeperWins,
                    drawGuessWins,
                    tacitQuizWins,
                    tacitQuizSameAnswers,
                    quickQuizWins,
                    gobangWins,
                    turtleSoupWins,
                    oldLibraryWins,
                    oldLibraryCompletions,
                    pendingOldTennisBall(dailyCounterMapper, accountId, rows)));
            profile.setInteractionStatus(buildInteractionStatus(dailyCounterMapper, accountId, todayText));
            profile.setDailyCompanionStatus(buildDailyCompanionStatus(dailyCounterMapper, accountId, todayText, rows));
            profile.setDailySaying(PetDailySayingService.currentState(session, accountId, todayText));
            profile.setRecentSayings(PetDailySayingService.recentSayings(session, accountId));
            profile.setTrainingStatus(buildTrainingStatus(trainingMapper, accountId));
            profile.setShopStatus(buildShopStatus(dailyCounterMapper, accountId, now));
            profile.setArcadeStatus(buildArcadeStatus(dailyCounterMapper, accountId, todayText, flip7State));
            if (energyRefreshed || legacyChestMigrated || exploreSettled || dogStageChanged || flip7State.changed) {
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

    private static PetShopStatusDTO buildShopStatus(PetDailyCounterMapper mapper, long accountId, long now) {
        long periodStartAt = currentShopShelfPeriodStartAt(now);
        int paidRefreshesUsed = findDailyCounterValue(mapper, accountId, shopShelfCounterDate(periodStartAt),
                COUNTER_SHOP_SHELF_PAID_REFRESH);
        int normalizedRefreshes = Math.min(SHOP_SHELF_MAX_PAID_REFRESHES, Math.max(0, paidRefreshesUsed));
        long periodIndex = periodStartAt / SHOP_SHELF_REFRESH_INTERVAL_MILLIS;
        long seed = accountId * 1_103_515_245L + periodIndex * 10_009L + normalizedRefreshes * 917_647L;
        List<String> rareItems = new ArrayList<>(LUCKY_BAG_RARE_ITEM_IDS);
        List<String> normalItems = new ArrayList<>(LUCKY_BAG_NORMAL_ITEM_IDS);
        Collections.shuffle(rareItems, new Random(seed ^ 0x5DEECE66DL));
        Collections.shuffle(normalItems, new Random(seed ^ 0x9E3779B97F4A7C15L));
        Integer nextPaidRefreshCost = normalizedRefreshes < SHOP_SHELF_PAID_REFRESH_COSTS.size()
                ? SHOP_SHELF_PAID_REFRESH_COSTS.get(normalizedRefreshes)
                : null;
        return new PetShopStatusDTO(
                rareItems.get(0),
                new ArrayList<>(normalItems.subList(0, Math.min(SHOP_SHELF_NORMAL_ITEM_COUNT, normalItems.size()))),
                dailySkinShopItemId(accountId, periodIndex, normalizedRefreshes),
                periodStartAt,
                periodStartAt + SHOP_SHELF_REFRESH_INTERVAL_MILLIS,
                normalizedRefreshes,
                SHOP_SHELF_MAX_PAID_REFRESHES,
                new ArrayList<>(SHOP_SHELF_PAID_REFRESH_COSTS),
                nextPaidRefreshCost);
    }

    private static PetArcadeStatusDTO buildArcadeStatus(PetDailyCounterMapper mapper, long accountId, String today,
                                                        Flip7GameState flip7State) {
        int dailyFreeUsed = Math.min(TREASURE_HUNT_DAILY_FREE_LIMIT, Math.max(0,
                findDailyCounterValue(mapper, accountId, today, DAILY_COUNTER_TREASURE_HUNT_FREE_USED)));
        int bonusSpins = Math.max(0,
                findLifetimeCounterValue(mapper, accountId, COUNTER_TREASURE_HUNT_BONUS_SPINS));
        int flip7DailyFreeUsed = Math.min(FLIP7_DAILY_FREE_LIMIT, Math.max(0,
                findDailyCounterValue(mapper, accountId, today, DAILY_COUNTER_FLIP7_FREE_USED)));
        List<PetTreasureHuntProbabilityDTO> rewardProbabilities = treasureRewardProbabilityRows();
        return new PetArcadeStatusDTO(new PetTreasureHuntStatusDTO(
                today,
                TREASURE_HUNT_DAILY_FREE_LIMIT,
                dailyFreeUsed,
                Math.max(0, TREASURE_HUNT_DAILY_FREE_LIMIT - dailyFreeUsed),
                bonusSpins,
                TREASURE_HUNT_PAID_COST,
                PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT,
                TREASURE_HUNT_SKIN_FRAGMENTS_PER_SKIN,
                new ArrayList<>(rewardProbabilities),
                new ArrayList<>(rewardProbabilities)),
                new PetFlip7StatusDTO(
                        today,
                        FLIP7_DAILY_FREE_LIMIT,
                        flip7DailyFreeUsed,
                        Math.max(0, FLIP7_DAILY_FREE_LIMIT - flip7DailyFreeUsed),
                        FLIP7_PAID_COST,
                        flip7State.drawPile.size(),
                        flip7State.discardPile.size(),
                        buildFlip7CardCounts(createOrderedFlip7DeckKeys(), createOrderedFlip7DeckKeys()),
                        buildFlip7CardCounts(createOrderedFlip7DeckKeys(), flip7State.drawPile),
                        copyFlip7RoundForClient(flip7State.activeRound, flip7State.drawPile.size())));
    }

    private static List<PetTreasureHuntProbabilityDTO> treasureRewardProbabilityRows() {
        List<PetTreasureHuntProbabilityDTO> rows = new ArrayList<>();
        for (TreasureSlotOption option : TREASURE_HUNT_SLOT_OPTIONS) {
            rows.add(new PetTreasureHuntProbabilityDTO(
                    option.type,
                    option.label,
                    option.probabilityBp * 10_000D / TREASURE_HUNT_ROLL_SCALE,
                    option.isBones() ? option.boneAmount : option.quantity));
        }
        return rows;
    }

    private static String dailySkinShopItemId(long accountId, long periodIndex, int refreshIndex) {
        if (DAILY_SKIN_SHOP_ITEM_IDS.isEmpty()) {
            return null;
        }
        long seed = accountId * 2_654_435_761L + periodIndex * 1_000_003L;
        List<String> skinItems = new ArrayList<>(DAILY_SKIN_SHOP_ITEM_IDS);
        Collections.shuffle(skinItems, new Random(seed ^ 0x6C8E9CF570932BD5L));
        return skinItems.get(Math.floorMod(refreshIndex, skinItems.size()));
    }

    private static long currentShopShelfPeriodStartAt(long now) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime currentTime = Instant.ofEpochMilli(now).atZone(zone);
        long dayStartAt = currentTime.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
        long elapsedToday = Math.max(0L, now - dayStartAt);
        return dayStartAt + Math.floorDiv(elapsedToday, SHOP_SHELF_REFRESH_INTERVAL_MILLIS) * SHOP_SHELF_REFRESH_INTERVAL_MILLIS;
    }

    private static String shopShelfCounterDate(long periodStartAt) {
        return "shop_shelf:" + periodStartAt;
    }

    private static PetPendingOldTennisBallDTO pendingOldTennisBall(PetDailyCounterMapper mapper,
                                                                   long accountId,
                                                                   List<PetDogRecord> dogs) {
        String pendingCounter = firstPendingOldTennisCounter(mapper, accountId);
        if (pendingCounter == null) {
            return null;
        }
        String dogId = pendingCounter.substring(EASTER_OLD_TENNIS_PENDING_PREFIX.length());
        String dogName = "";
        for (PetDogRecord dog : dogs) {
            if (dog.getId().equals(dogId)) {
                dogName = dog.getName();
                break;
            }
        }
        return new PetPendingOldTennisBallDTO(dogId, dogName);
    }

    private static String firstPendingOldTennisCounter(PetDailyCounterMapper mapper, long accountId) {
        List<String> counters = mapper.listCountersByPrefix(accountId, COUNTER_DATE_LIFETIME,
                EASTER_OLD_TENNIS_PENDING_PREFIX);
        return counters.isEmpty() ? null : counters.get(0);
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
        BreedConfig breedConfig = BreedConfig.of(breed);
        if (breedConfig == null) {
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
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if (breedConfig.hidden && !isHiddenBreedUnlocked(session, mapper, accountId, breed)) {
                throw new IllegalArgumentException("该隐藏品种尚未解锁");
            }
            boolean paidPublicAdoption = !breedConfig.hidden && mapper.countByOwner(accountId) > 0;
            if (paidPublicAdoption) {
                if (assets.getBones() < PUBLIC_DOG_ADOPTION_PRICE
                        || assetsMapper.decrementBonesIfEnough(accountId, PUBLIC_DOG_ADOPTION_PRICE, now) <= 0) {
                    throw new IllegalArgumentException("骨头币不足");
                }
            }

            mapper.insert(PetDogRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .ownerId(accountId)
                    .name(name)
                    .breed(breed)
                    .stage("puppy")
                    .bond(BreedConfig.DEFAULT_BOND)
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
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            if (refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now)) {
                assets = assetsMapper.findByAccountId(accountId);
            }
            PetDogRecord dog = StrUtil.isBlank(dogId) ? null : dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能喂自己的狗狗");
            }

            if (assetsMapper.decrementFoodIfEnough(accountId, now) <= 0) {
                throw new IllegalArgumentException("狗粮不足");
            }

            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                    DAILY_COUNTER_FEED_BOND_PREFIX, now);

            if (assets.getEnergy() < energyLimit
                    && counterMapper.incrementIfUnderLimit(accountId, today,
                    DAILY_COUNTER_FEED_FOOD, DAILY_FEED_LIMIT, now) > 0) {
                assetsMapper.addEnergyIfUnderLimit(accountId, 1, energyLimit, now);
            }
            dogMapper.updateCareStats(dog.getId(), accountId, bond, now);
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

    public static PetProfileDTO resolveOldTennisBall(long accountId, PetResolveOldTennisBallDTO request) {
        synchronized (accountLock(accountId)) {
            return resolveOldTennisBallLocked(accountId, request);
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
                    dogMapper.updateCareStats(dog.getId(), accountId, bond, now);
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
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            if (refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now)) {
                assets = assetsMapper.findByAccountId(accountId);
            }
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
                throw new IllegalArgumentException("这只狗狗今天已经散步过了");
            }
            if (assets.getEnergy() <= 0) {
                throw new IllegalArgumentException("狗狗活力不足");
            }

            if (assetsMapper.decrementEnergyIfEnough(accountId, 1, now) <= 0) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            if (counterMapper.incrementIfUnderLimit(accountId, today, outingCounter, 1, now) <= 0) {
                throw new IllegalArgumentException("这只狗狗今天已经散步过了");
            }

            String totalCounter = DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX + dog.getId();
            if (findDailyCounterValue(counterMapper, accountId, today, totalCounter) < DAILY_DOG_BOND_LIMIT
                    && counterMapper.incrementIfUnderLimit(accountId, today,
                    totalCounter, DAILY_DOG_BOND_LIMIT, now) > 0) {
                int bond = clampDogStat(dog.getBond() + 1);
                if (dogMapper.updateCareStats(dog.getId(), accountId, bond, now) <= 0) {
                    throw new IllegalArgumentException("狗狗散步失败，请刷新后重试");
                }
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO resolveOldTennisBallLocked(long accountId, PetResolveOldTennisBallDTO request) {
        String choice = request == null ? null : StrUtil.trim(request.getChoice());
        if (!"return".equals(choice) && !"collect".equals(choice)) {
            throw new IllegalArgumentException("请选择扔回去或收藏旧网球");
        }

        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            String pendingCounter = firstPendingOldTennisCounter(counterMapper, accountId);
            if (pendingCounter == null) {
                throw new IllegalArgumentException("没有待处理的旧网球");
            }
            String dogId = pendingCounter.substring(EASTER_OLD_TENNIS_PENDING_PREFIX.length());
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("发现旧网球的狗狗不存在");
            }

            if ("collect".equals(choice)) {
                session.getMapper(PetCollectionMapper.class).addCollection(accountId,
                        EASTER_OLD_TENNIS_COLLECTION_ID, now);
            } else {
                int bond = grantDailyDogBondAmount(counterMapper, accountId, today, dog,
                        DAILY_COUNTER_OLD_TENNIS_BOND_PREFIX, EASTER_OLD_TENNIS_RETURN_BOND, now);
                if (bond != dog.getBond()
                        && dogMapper.updateCareStats(dog.getId(), accountId, bond, now) <= 0) {
                    throw new IllegalArgumentException("旧网球亲密度更新失败");
                }
            }
            counterMapper.deleteCounter(accountId, COUNTER_DATE_LIFETIME, pendingCounter);
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
            return findCollectionCount(session.getMapper(PetCollectionMapper.class),
                    accountId, SHIBA_UNLOCK_COLLECTION_ID) > 0
                    || session.getMapper(PetCheckinMapper.class).countByAccountId(accountId) >= SHIBA_CHECKIN_PITY_COUNT;
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

    public static PetProfileDTO shopRefresh(long accountId) {
        synchronized (accountLock(accountId)) {
            return shopRefreshLocked(accountId);
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

    public static PetProfileDTO exploreCancel(long accountId, PetExploreOpenDTO request) {
        synchronized (accountLock(accountId)) {
            return exploreCancelLocked(accountId, request);
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

    public static PetTreasureHuntSpinResultDTO treasureHuntSpin(long accountId) {
        synchronized (accountLock(accountId)) {
            return treasureHuntSpinLocked(accountId);
        }
    }

    public static PetFlip7ActionResultDTO flip7Play(long accountId) {
        synchronized (accountLock(accountId)) {
            return flip7PlayLocked(accountId);
        }
    }

    public static PetFlip7ActionResultDTO flip7Draw(long accountId) {
        synchronized (accountLock(accountId)) {
            return flip7DrawLocked(accountId);
        }
    }

    public static PetFlip7ActionResultDTO flip7Stand(long accountId) {
        synchronized (accountLock(accountId)) {
            return flip7StandLocked(accountId);
        }
    }

    public static PetProfileDTO treasureHuntRedeemSkin(long accountId, PetTreasureHuntRedeemSkinDTO request) {
        synchronized (accountLock(accountId)) {
            return treasureHuntRedeemSkinLocked(accountId, request);
        }
    }

    public static PetProfileDTO grantTreasureHuntBonusSpins(long accountId, int quantity) {
        synchronized (accountLock(accountId)) {
            return grantTreasureHuntBonusSpinsLocked(accountId, quantity);
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

    public static void applyMiniGameRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
        List<Long> normalizedAccountIds = normalizeRoomBonusAccountIds(accountIds);
        if (!MINI_GAME_ROOM_BONUS_GAMES.contains(game)
                || durationSeconds < MINI_GAME_MIN_REWARD_SECONDS
                || normalizedAccountIds.size() != 2) {
            return;
        }
        List<Long> lockIds = new ArrayList<>(normalizedAccountIds);
        Collections.sort(lockIds);
        runWithAccountLocks(lockIds, 0, () -> applyMiniGameRoomBonusLocked(normalizedAccountIds));
    }
    public static void recordTacitQuizSameAnswer(long accountId) {
        synchronized (accountLock(accountId)) {
            recordTacitQuizSameAnswerLocked(accountId);
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
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            if (refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now)) {
                assets = assetsMapper.findByAccountId(accountId);
            }
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能派遣自己的狗狗探险");
            }
            updateDogGrowthStage(dogMapper, counterMapper, accountId, dog, now);
            if (!"idle".equals(dog.getStatus())) {
                throw new IllegalArgumentException("只有空闲狗狗可以去探险");
            }
            if (assets.getEnergy() < energyCost) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            TrainingSnapshot trainingSnapshot = buildExploreTrainingSnapshot(
                    session.getMapper(PetTrainingMapper.class), accountId, dog.getExploreSkillId());
            long exploreEndsAt = now + exploreDurationMillis(durationHours, trainingSnapshot);
            if (EXPLORE_LOCATION_MYSTERY_CAVE.equals(location)) {
                ensureMysteryCaveAvailable(session, accountId);
            } else {
                ensureExploreLocationUnlocked(session, accountId, location);
                ensureExploreStageUnlocked(dog, durationHours);
                if (exploreChestCount(session.getMapper(PetExploreChestMapper.class),
                        session.getMapper(PetItemMapper.class), accountId, exploreChestItemId(location))
                        >= MAX_EXPLORE_CHEST_COUNT) {
                    throw new IllegalArgumentException(exploreChestFullError(location));
                }
            }
            if (counterMapper.incrementIfUnderLimit(accountId, today, DAILY_COUNTER_EXPLORE_START,
                    DAILY_EXPLORE_START_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日探险派遣次数已达上限");
            }
            if (assetsMapper.decrementEnergyIfEnough(accountId, energyCost, now) <= 0) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            if (dogMapper.startExplore(dogId, accountId, location, exploreEndsAt,
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
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            addExploreBones(assetsMapper, accountId,
                    applyExploreBonesTraining(effectiveExploreBaseBones(collectionMapper, accountId,
                            exploreBaseBones(durationHours)), dog), rewards, now);
            applyExploreRolls(session, accountId, durationHours, dog, location, rewards, today, now);
            if (EXPLORE_LOCATION_MYSTERY_CAVE.equals(location)) {
                collectionMapper.addCollection(accountId, MYSTERY_CAVE_COMPLETED_COLLECTION_ID, now);
                rewards.add(new PetExploreRewardDTO("collection", MYSTERY_CAVE_COMPLETED_COLLECTION_ID, 1));
            } else {
                recordExploreCompletion(counterMapper, accountId, location, now);
            }
            grantOutingBondIfAvailable(counterMapper, dogMapper, accountId, today, dog, now);
            grantFirstExploreFreeLearnIfEligible(session.getMapper(PetTrainingMapper.class),
                    accountId, durationHours, now);
            if (dogMapper.openExplore(dogId, accountId, now) <= 0) {
                throw new IllegalArgumentException("探险开箱失败，请刷新后重试");
            }
            PetAssetsRecord assets = assetsMapper.findByAccountId(accountId);
            int energyLimit = effectiveEnergyLimit(collectionMapper, accountId, assets.getEnergyLimit());
            applyExploreEnergyTraining(assetsMapper, accountId, dog, energyLimit, rewards, now);
            session.commit();
        }

        return new PetExploreOpenResultDTO(profile(accountId), rewards);
    }

    private static PetProfileDTO exploreCancelLocked(long accountId, PetExploreOpenDTO request) {
        String dogId = request == null ? null : StrUtil.trim(request.getDogId());
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("狗狗请求内容无效");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能取消自己的狗狗探险");
            }
            if (!"exploring".equals(dog.getStatus())) {
                throw new IllegalArgumentException("狗狗当前没有正在进行的探险");
            }
            if (dogMapper.resetExplore(dogId, accountId, now) <= 0) {
                throw new IllegalArgumentException("取消探险失败，请刷新后重试");
            }
            session.commit();
        }

        return profile(accountId);
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, ITEM_LUCKY_DAY, 1,
                    ITEM_LEDGER_SPEND, ITEM_LEDGER_SOURCE_USE_ITEM, null, null, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetExploreOpenResultDTO openBackHillChestLocked(long accountId, PetUseItemDTO request) {
        String itemId = request == null ? null : StrUtil.trim(request.getItemId());
        String chestId = request == null ? null : StrUtil.trim(request.getChestId());
        Integer quantity = request == null ? null : request.getQuantity();
        String baseLocation = exploreLocationByChestItemId(itemId);
        if (baseLocation == null) {
            throw new IllegalArgumentException("暂不支持该道具");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("道具使用数量必须大于 0");
        }
        if (quantity > MAX_EXPLORE_CHEST_COUNT) {
            throw new IllegalArgumentException("一次最多打开 99 个箱子");
        }
        if (StrUtil.isNotBlank(chestId) && quantity != 1) {
            throw new IllegalArgumentException("指定箱子一次只能打开 1 个");
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        List<PetExploreRewardDTO> rewards = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetExploreChestMapper chestMapper = session.getMapper(PetExploreChestMapper.class);
            PetItemLedgerMapper ledgerMapper = session.getMapper(PetItemLedgerMapper.class);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            for (int i = 0; i < quantity; i++) {
                String location = baseLocation;
                PetDogRecord snapshotDog = null;
                int durationHours = EXPLORE_ONE_HOUR;
                PetExploreChestRecord chest = null;
                if (StrUtil.isNotBlank(chestId)) {
                    chest = chestMapper.findAvailableByIdAndAccountId(chestId, accountId);
                    if (chest == null || !itemId.equals(chest.getChestItemId())) {
                        throw new IllegalArgumentException("箱子不存在或已打开");
                    }
                } else {
                    for (PetExploreChestRecord candidate : chestMapper.listAvailableByAccountId(accountId)) {
                        if (itemId.equals(candidate.getChestItemId())) {
                            chest = candidate;
                            break;
                        }
                    }
                }
                if (chest != null) {
                    location = StrUtil.trim(chest.getLocation());
                    if (!isSupportedExploreLocation(location)) {
                        throw new IllegalArgumentException("箱子地点数据异常");
                    }
                    durationHours = isSupportedExploreDuration(chest.getDurationHours())
                            ? chest.getDurationHours()
                            : EXPLORE_ONE_HOUR;
                    snapshotDog = exploreSnapshotDog(chest);
                    if (chestMapper.markOpened(chest.getId(), accountId, now) <= 0) {
                        throw new IllegalArgumentException("箱子已打开，请刷新后重试");
                    }
                    recordItemLedger(ledgerMapper, accountId, itemId, 1, ITEM_LEDGER_SPEND,
                            ITEM_LEDGER_SOURCE_OPEN_EXPLORE_CHEST, chest.getId(), null, now);
                } else {
                    if (itemMapper.decrementItemIfEnough(accountId, itemId, 1, now) <= 0) {
                        throw new IllegalArgumentException("道具数量不足");
                    }
                    recordItemLedger(ledgerMapper, accountId, itemId, 1, ITEM_LEDGER_SPEND,
                            ITEM_LEDGER_SOURCE_OPEN_EXPLORE_CHEST, null, null, now);
                }
                addExploreBones(assetsMapper, accountId, applyExploreBonesTraining(effectiveExploreBaseBones(
                        collectionMapper, accountId, exploreBaseBones(durationHours)), snapshotDog),
                        rewards, now);
                applyExploreRolls(session, accountId, durationHours, snapshotDog, location, rewards, today, now);
                if (snapshotDog != null) {
                    PetAssetsRecord assets = assetsMapper.findByAccountId(accountId);
                    int energyLimit = effectiveEnergyLimit(collectionMapper, accountId, assets.getEnergyLimit());
                    applyExploreEnergyTraining(assetsMapper, accountId, snapshotDog, energyLimit, rewards, now);
                }
            }
            session.commit();
        }

        return new PetExploreOpenResultDTO(profile(accountId), rewards);
    }

    private static PetProfileDTO useFeastItem(long accountId, String dogId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            if (refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now)) {
                assets = assetsMapper.findByAccountId(accountId);
            }
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("只能给自己的狗狗使用道具");
            }
            if (assets.getEnergy() >= energyLimit) {
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, ITEM_FEAST, 1,
                    ITEM_LEDGER_SPEND, ITEM_LEDGER_SOURCE_USE_ITEM, dogId, null, now);
            assets.setEnergy(energyLimit);
            assets.setEnergyDate(today);
            assets.setEnergyLimit(energyLimit);
            assets.setUpdatedAt(now);
            assetsMapper.update(assets);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO useExpressItem(long accountId, String dogId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now);
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, ITEM_EXPRESS, 1,
                    ITEM_LEDGER_SPEND, ITEM_LEDGER_SOURCE_USE_ITEM, dogId, null, now);
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
            PetDogRecord dog = dogMapper.findByIdAndOwner(dogId, accountId);
            if (dog != null) {
                grantOutingBondIfAvailable(session.getMapper(PetDailyCounterMapper.class), dogMapper,
                        accountId, LocalDate.now().toString(), dog, now);
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static PetTreasureHuntSpinResultDTO treasureHuntSpinLocked(long accountId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        String spinSource;
        int paidCost = 0;
        int boneReward;
        PetTreasureHuntExtraRewardDTO extraReward;
        List<PetTreasureHuntExtraRewardDTO> extraRewards;
        int bonusSpinReward;
        List<String> symbols;
        List<String> detailLines;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            int freeUsed = findDailyCounterValue(counterMapper, accountId, today,
                    DAILY_COUNTER_TREASURE_HUNT_FREE_USED);
            if (freeUsed < TREASURE_HUNT_DAILY_FREE_LIMIT
                    && counterMapper.incrementIfUnderLimit(accountId, today,
                    DAILY_COUNTER_TREASURE_HUNT_FREE_USED, TREASURE_HUNT_DAILY_FREE_LIMIT, now) > 0) {
                spinSource = "daily_free";
            } else if (findLifetimeCounterValue(counterMapper, accountId, COUNTER_TREASURE_HUNT_BONUS_SPINS) > 0
                    && counterMapper.decrementIfEnough(accountId, COUNTER_DATE_LIFETIME,
                    COUNTER_TREASURE_HUNT_BONUS_SPINS, 1, now) > 0) {
                spinSource = "bonus";
            } else {
                if (assetsMapper.decrementBonesIfEnough(accountId, TREASURE_HUNT_PAID_COST, now) <= 0) {
                    throw new IllegalArgumentException("骨头币不足，暂时不能继续寻宝");
                }
                spinSource = "paid";
                paidCost = TREASURE_HUNT_PAID_COST;
            }

            TreasureHuntSettlement settlement = settleTreasureHuntSpin(session, accountId, now);
            boneReward = settlement.boneReward;
            extraRewards = settlement.extraRewards;
            extraReward = extraRewards.isEmpty() ? null : extraRewards.get(0);
            bonusSpinReward = settlement.bonusSpinReward;
            symbols = settlement.symbols;
            detailLines = settlement.detailLines;
            if (boneReward > 0 && assetsMapper.addBones(accountId, boneReward, now) <= 0) {
                throw new IllegalArgumentException("寻宝奖励发放失败");
            }
            session.commit();
        }

        return new PetTreasureHuntSpinResultDTO(
                profileLocked(accountId),
                spinSource,
                paidCost,
                boneReward,
                extraReward,
                extraRewards,
                bonusSpinReward,
                symbols,
                detailLines);
    }

    private static PetFlip7ActionResultDTO flip7PlayLocked(long accountId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        String playSource;
        int paidCost = 0;
        PetFlip7RoundDTO round;
        String event = FLIP7_EVENT_STARTED;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetFlip7StateMapper stateMapper = session.getMapper(PetFlip7StateMapper.class);
            Flip7GameState state = ensureFlip7State(stateMapper, accountId, today, now);

            if (state.activeRound != null && FLIP7_STATE_ACTIVE.equals(state.activeRound.getState())) {
                round = copyFlip7RoundForClient(state.activeRound, state.drawPile.size());
                updateFlip7RoundControls(round, state.drawPile.size());
                event = FLIP7_EVENT_RESUMED;
                if (state.changed) {
                    saveFlip7State(stateMapper, state, now);
                    session.commit();
                }
                return buildFlip7ActionResult(accountId, event, round, null, "本轮翻转7继续进行中");
            }

            int freeUsed = findDailyCounterValue(counterMapper, accountId, today, DAILY_COUNTER_FLIP7_FREE_USED);
            if (freeUsed < FLIP7_DAILY_FREE_LIMIT
                    && counterMapper.incrementIfUnderLimit(accountId, today,
                    DAILY_COUNTER_FLIP7_FREE_USED, FLIP7_DAILY_FREE_LIMIT, now) > 0) {
                playSource = "daily_free";
            } else {
                if (assetsMapper.decrementBonesIfEnough(accountId, FLIP7_PAID_COST, now) <= 0) {
                    throw new IllegalArgumentException("骨头币不足，暂时不能继续玩翻转7");
                }
                playSource = "paid";
                paidCost = FLIP7_PAID_COST;
            }

            round = new PetFlip7RoundDTO(
                    UUID.randomUUID().toString(),
                    FLIP7_STATE_ACTIVE,
                    playSource,
                    paidCost,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    !state.drawPile.isEmpty(),
                    false,
                    false,
                    now,
                    null);
            round.getDetailLines().add(playSource.equals("daily_free")
                    ? "本轮使用今日免费次数开始。"
                    : "本轮消耗 " + paidCost + " 骨头币开始。");
            state.activeRound = round;
            saveFlip7State(stateMapper, state, now);
            session.commit();
        }

        return buildFlip7ActionResult(accountId, event, round, null, "本轮翻转7已开始");
    }

    private static PetFlip7ActionResultDTO flip7DrawLocked(long accountId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        PetFlip7RoundDTO round;
        PetFlip7CardDTO drawnCard = null;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetFlip7StateMapper stateMapper = session.getMapper(PetFlip7StateMapper.class);
            Flip7GameState state = ensureFlip7State(stateMapper, accountId, today, now);
            round = requireActiveFlip7Round(state.activeRound);

            if (state.drawPile.isEmpty()) {
                finishFlip7Round(round, FLIP7_STATE_DECK_EMPTY, now);
                grantFlip7RewardIfNeeded(assetsMapper, accountId, round, now);
                state.activeRound = null;
                saveFlip7State(stateMapper, state, now);
                session.commit();
                return buildFlip7ActionResult(accountId, FLIP7_EVENT_SETTLED, round, null, "牌堆已经翻完，本轮结算完成");
            }

            String cardKey = state.drawPile.remove(0);
            state.discardPile.add(cardKey);
            Flip7Card card = flip7CardFromKey(cardKey);
            drawnCard = applyFlip7Card(round, card, state.drawPile.size(), now);

            if (FLIP7_STATE_ACTIVE.equals(round.getState())) {
                state.activeRound = round;
            } else {
                grantFlip7RewardIfNeeded(assetsMapper, accountId, round, now);
                state.activeRound = null;
            }
            saveFlip7State(stateMapper, state, now);
            session.commit();
        }

        String event = FLIP7_STATE_ACTIVE.equals(round.getState()) ? FLIP7_EVENT_DRAWN : FLIP7_EVENT_SETTLED;
        return buildFlip7ActionResult(accountId, event, round, drawnCard, formatFlip7RoundMessage(round));
    }

    private static PetFlip7ActionResultDTO flip7StandLocked(long accountId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        PetFlip7RoundDTO round;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetFlip7StateMapper stateMapper = session.getMapper(PetFlip7StateMapper.class);
            Flip7GameState state = ensureFlip7State(stateMapper, accountId, today, now);
            round = requireActiveFlip7Round(state.activeRound);
            if (round.getCards() == null || round.getCards().isEmpty()) {
                throw new IllegalArgumentException("请先翻一张牌再停牌");
            }
            if (round.getForcedDrawsRemaining() > 0) {
                throw new IllegalArgumentException("Flip Three 还需要继续翻牌，暂时不能停牌");
            }

            finishFlip7Round(round, FLIP7_STATE_STOOD, now);
            grantFlip7RewardIfNeeded(assetsMapper, accountId, round, now);
            state.activeRound = null;
            saveFlip7State(stateMapper, state, now);
            session.commit();
        }

        return buildFlip7ActionResult(accountId, FLIP7_EVENT_STOOD, round, null, formatFlip7RoundMessage(round));
    }

    private static PetFlip7ActionResultDTO buildFlip7ActionResult(long accountId,
                                                                  String event,
                                                                  PetFlip7RoundDTO round,
                                                                  PetFlip7CardDTO drawnCard,
                                                                  String message) {
        PetProfileDTO profile = profileLocked(accountId);
        PetFlip7StatusDTO status = profile.getArcadeStatus() == null ? null : profile.getArcadeStatus().getFlip7();
        int deckRemaining = status == null ? 0 : status.getDeckRemaining();
        return new PetFlip7ActionResultDTO(
                profile,
                status,
                copyFlip7RoundForClient(round, deckRemaining),
                event,
                drawnCard,
                message);
    }

    private static PetFlip7RoundDTO requireActiveFlip7Round(PetFlip7RoundDTO round) {
        if (round == null || !FLIP7_STATE_ACTIVE.equals(round.getState())) {
            throw new IllegalArgumentException("请先开始一轮翻转7");
        }
        normalizeFlip7Round(round);
        return round;
    }

    private static PetFlip7CardDTO applyFlip7Card(PetFlip7RoundDTO round,
                                                  Flip7Card card,
                                                  int deckRemaining,
                                                  long now) {
        normalizeFlip7Round(round);
        if (round.getForcedDrawsRemaining() > 0) {
            round.setForcedDrawsRemaining(round.getForcedDrawsRemaining() - 1);
        }

        boolean usedSecondChance = false;
        boolean bust = false;
        Set<Integer> uniqueNumbers = flip7UniqueNumbers(round);
        List<String> detailLines = round.getDetailLines();

        if (card.isNumber()) {
            int value = card.value;
            if (uniqueNumbers.contains(value)) {
                if (round.isHasSecondChance()) {
                    round.setHasSecondChance(false);
                    usedSecondChance = true;
                    detailLines.add("翻出重复数字 " + value + "，Second Chance 抵消，本张进入弃牌堆。");
                } else {
                    bust = true;
                    round.setState(FLIP7_STATE_BUST);
                    detailLines.add("翻出重复数字 " + value + "，爆掉，本轮 0 分。");
                }
            } else {
                uniqueNumbers.add(value);
                round.setNumberSum(round.getNumberSum() + value);
                detailLines.add("翻出数字 " + value + "，当前已有 " + uniqueNumbers.size() + " 个不同数字。");
                if (uniqueNumbers.size() >= FLIP7_UNIQUE_TARGET) {
                    round.setFlip7Bonus(FLIP7_BONUS);
                    round.setState(FLIP7_STATE_FLIP7);
                    detailLines.add("凑齐 7 个不同数字，额外获得 " + FLIP7_BONUS + " 分。");
                }
            }
        } else if (card.isModifier()) {
            if (card.modifier == null) {
                round.setMultiplier(round.getMultiplier() * 2);
                detailLines.add("翻出 x2 修正牌，数字牌总和翻倍。");
            } else {
                round.setModifierBonus(round.getModifierBonus() + card.modifier);
                detailLines.add("翻出 +" + card.modifier + " 修正牌。");
            }
        } else if ("second_chance".equals(card.action)) {
            if (round.isHasSecondChance()) {
                detailLines.add("翻出 Second Chance，但已经持有，本张进入弃牌堆。");
            } else {
                round.setHasSecondChance(true);
                detailLines.add("翻出 Second Chance，可抵消下一次重复数字。");
            }
        } else if ("flip_three".equals(card.action)) {
            round.setForcedDrawsRemaining(round.getForcedDrawsRemaining() + FLIP7_FLIP_THREE_COUNT);
            detailLines.add("翻出 Flip Three，接下来还要强制翻 " + FLIP7_FLIP_THREE_COUNT + " 张。");
        } else if ("freeze".equals(card.action)) {
            round.setState(FLIP7_STATE_FROZEN);
            detailLines.add("翻出 Freeze，立即停牌结算。");
        }

        int scoreAfterCard = bust ? 0 : calculateFlip7Score(
                round.getNumberSum(),
                round.getModifierBonus(),
                round.getMultiplier(),
                round.getFlip7Bonus());
        PetFlip7CardDTO dto = new PetFlip7CardDTO(
                card.type,
                card.label,
                card.value,
                card.modifier,
                card.action,
                usedSecondChance,
                bust,
                scoreAfterCard);
        round.getCards().add(dto);
        round.setScorePreview(scoreAfterCard);
        round.setScore(scoreAfterCard);

        if (FLIP7_STATE_ACTIVE.equals(round.getState()) && deckRemaining <= 0) {
            round.setState(FLIP7_STATE_DECK_EMPTY);
            detailLines.add("今天这副牌已经全部翻完，本轮按当前分数结算。");
        }
        if (!FLIP7_STATE_ACTIVE.equals(round.getState())) {
            finishFlip7Round(round, round.getState(), now);
        } else {
            updateFlip7RoundControls(round, deckRemaining);
        }
        return dto;
    }

    private static void finishFlip7Round(PetFlip7RoundDTO round, String result, long now) {
        normalizeFlip7Round(round);
        round.setState(result);
        int score = FLIP7_STATE_BUST.equals(result)
                ? 0
                : calculateFlip7Score(round.getNumberSum(), round.getModifierBonus(),
                round.getMultiplier(), round.getFlip7Bonus());
        round.setScorePreview(score);
        round.setScore(score);
        round.setBoneReward(score);
        round.setCanDraw(false);
        round.setCanStand(false);
        round.setSettledAt(now);
        round.getDetailLines().add("本轮得分 " + score + "，发放骨头币 " + score + "。");
    }

    private static void grantFlip7RewardIfNeeded(PetAssetsMapper assetsMapper,
                                                 long accountId,
                                                 PetFlip7RoundDTO round,
                                                 long now) {
        int boneReward = Math.max(0, round.getBoneReward());
        if (boneReward > 0 && assetsMapper.addBones(accountId, boneReward, now) <= 0) {
            throw new IllegalArgumentException("翻转7奖励发放失败");
        }
    }

    private static String formatFlip7RoundMessage(PetFlip7RoundDTO round) {
        if (round == null) {
            return "翻转7状态已更新";
        }
        if (FLIP7_STATE_ACTIVE.equals(round.getState())) {
            return "已翻出 " + round.getCards().size() + " 张牌，当前 " + round.getScorePreview() + " 分";
        }
        if (round.getBoneReward() > 0) {
            return "本轮得分 " + round.getScore() + "，获得骨头币" + round.getBoneReward();
        }
        return "本轮爆掉，未获得骨头币";
    }

    private static void updateFlip7RoundControls(PetFlip7RoundDTO round, int deckRemaining) {
        normalizeFlip7Round(round);
        boolean active = FLIP7_STATE_ACTIVE.equals(round.getState());
        round.setCanDraw(active && deckRemaining > 0);
        round.setCanStand(active && !round.getCards().isEmpty() && round.getForcedDrawsRemaining() <= 0);
    }

    private static Set<Integer> flip7UniqueNumbers(PetFlip7RoundDTO round) {
        Set<Integer> uniqueNumbers = new HashSet<>();
        if (round.getCards() == null) {
            return uniqueNumbers;
        }
        for (PetFlip7CardDTO card : round.getCards()) {
            if ("number".equals(card.getType()) && card.getValue() != null
                    && !card.isUsedSecondChance() && !card.isBust()) {
                uniqueNumbers.add(card.getValue());
            }
        }
        return uniqueNumbers;
    }

    private static void normalizeFlip7Round(PetFlip7RoundDTO round) {
        if (round.getCards() == null) {
            round.setCards(new ArrayList<>());
        }
        if (round.getDetailLines() == null) {
            round.setDetailLines(new ArrayList<>());
        }
        if (round.getMultiplier() <= 0) {
            round.setMultiplier(1);
        }
        if (StrUtil.isBlank(round.getState())) {
            round.setState(FLIP7_STATE_ACTIVE);
        }
        if (StrUtil.isBlank(round.getRoundId())) {
            round.setRoundId(UUID.randomUUID().toString());
        }
    }

    private static Flip7GameState ensureFlip7State(PetFlip7StateMapper mapper,
                                                   long accountId,
                                                   String today,
                                                   long now) {
        PetFlip7StateRecord record = mapper.findByAccountId(accountId);
        boolean changed = false;
        List<String> drawPile;
        List<String> discardPile;
        PetFlip7RoundDTO activeRound;

        if (record == null || !today.equals(record.getStateDate())) {
            record = PetFlip7StateRecord.builder()
                    .accountId(accountId)
                    .stateDate(today)
                    .drawPileJson("[]")
                    .discardPileJson("[]")
                    .activeRoundJson(null)
                    .updatedAt(now)
                    .build();
            drawPile = createFreshFlip7DeckKeys();
            discardPile = new ArrayList<>();
            activeRound = null;
            changed = true;
        } else {
            drawPile = parseFlip7Pile(record.getDrawPileJson());
            discardPile = parseFlip7Pile(record.getDiscardPileJson());
            activeRound = parseFlip7Round(record.getActiveRoundJson());
            if (activeRound != null && !FLIP7_STATE_ACTIVE.equals(activeRound.getState())) {
                activeRound = null;
                changed = true;
            }
            if (drawPile.isEmpty() && activeRound == null) {
                drawPile = createFreshFlip7DeckKeys();
                discardPile = new ArrayList<>();
                changed = true;
            }
        }

        Flip7GameState state = new Flip7GameState(record, drawPile, discardPile, activeRound, changed);
        if (changed) {
            saveFlip7State(mapper, state, now);
        }
        return state;
    }

    private static void saveFlip7State(PetFlip7StateMapper mapper, Flip7GameState state, long now) {
        state.record.setDrawPileJson(JSONUtil.toJsonStr(state.drawPile));
        state.record.setDiscardPileJson(JSONUtil.toJsonStr(state.discardPile));
        state.record.setActiveRoundJson(state.activeRound == null ? null : JSONUtil.toJsonStr(state.activeRound));
        state.record.setUpdatedAt(now);
        mapper.upsert(state.record);
        state.changed = false;
    }

    private static List<String> parseFlip7Pile(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSONUtil.toBean(json, new TypeReference<List<String>>() {}, false));
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static PetFlip7RoundDTO parseFlip7Round(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            PetFlip7RoundDTO round = JSONUtil.toBean(json, PetFlip7RoundDTO.class);
            normalizeFlip7Round(round);
            return round;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> createFreshFlip7DeckKeys() {
        List<Flip7Card> deck = new ArrayList<>(flip7DeckSupplier.get());
        if (deck.isEmpty()) {
            deck = createShuffledFlip7Deck();
        }
        List<String> keys = new ArrayList<>(deck.size());
        for (Flip7Card card : deck) {
            keys.add(flip7CardKey(card));
        }
        return keys;
    }

    private static List<String> createOrderedFlip7DeckKeys() {
        List<Flip7Card> deck = createOrderedFlip7Deck();
        List<String> keys = new ArrayList<>(deck.size());
        for (Flip7Card card : deck) {
            keys.add(flip7CardKey(card));
        }
        return keys;
    }

    private static List<PetFlip7CardCountDTO> buildFlip7CardCounts(List<String> totalDeck,
                                                                    List<String> remainingPile) {
        Map<String, Integer> totalCounts = countFlip7Keys(totalDeck);
        Map<String, Integer> remainingCounts = countFlip7Keys(remainingPile);
        Set<String> seen = new HashSet<>();
        List<PetFlip7CardCountDTO> rows = new ArrayList<>();
        for (String key : totalDeck) {
            if (!seen.add(key)) {
                continue;
            }
            Flip7Card card = flip7CardFromKey(key);
            rows.add(new PetFlip7CardCountDTO(
                    card.type,
                    card.label,
                    card.value,
                    card.modifier,
                    card.action,
                    totalCounts.getOrDefault(key, 0),
                    remainingCounts.getOrDefault(key, 0)));
        }
        return rows;
    }

    private static Map<String, Integer> countFlip7Keys(List<String> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : keys) {
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    private static PetFlip7RoundDTO copyFlip7RoundForClient(PetFlip7RoundDTO round, int deckRemaining) {
        if (round == null) {
            return null;
        }
        normalizeFlip7Round(round);
        PetFlip7RoundDTO copy = new PetFlip7RoundDTO(
                round.getRoundId(),
                round.getState(),
                round.getPlaySource(),
                round.getPaidCost(),
                new ArrayList<>(round.getCards()),
                new ArrayList<>(round.getDetailLines()),
                round.getNumberSum(),
                round.getModifierBonus(),
                round.getMultiplier(),
                round.getFlip7Bonus(),
                round.getScorePreview(),
                round.getScore(),
                round.getBoneReward(),
                round.getForcedDrawsRemaining(),
                round.isCanDraw(),
                round.isCanStand(),
                round.isHasSecondChance(),
                round.getStartedAt(),
                round.getSettledAt());
        updateFlip7RoundControls(copy, deckRemaining);
        if (!FLIP7_STATE_ACTIVE.equals(copy.getState())) {
            copy.setCanDraw(false);
            copy.setCanStand(false);
        }
        return copy;
    }

    private static String flip7CardKey(Flip7Card card) {
        if (card.isNumber()) {
            return FLIP7_CARD_NUMBER_PREFIX + card.value;
        }
        if (card.isModifier()) {
            return FLIP7_CARD_MODIFIER_PREFIX + card.label;
        }
        return FLIP7_CARD_ACTION_PREFIX + card.action;
    }

    private static Flip7Card flip7CardFromKey(String key) {
        if (key != null && key.startsWith(FLIP7_CARD_NUMBER_PREFIX)) {
            try {
                return Flip7Card.number(Integer.parseInt(key.substring(FLIP7_CARD_NUMBER_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                return Flip7Card.number(0);
            }
        }
        if (key != null && key.startsWith(FLIP7_CARD_MODIFIER_PREFIX)) {
            String label = key.substring(FLIP7_CARD_MODIFIER_PREFIX.length());
            if ("x2".equals(label)) {
                return Flip7Card.modifier("x2", null);
            }
            try {
                int modifier = Integer.parseInt(label.replace("+", ""));
                return Flip7Card.modifier("+" + modifier, modifier);
            } catch (NumberFormatException ignored) {
                return Flip7Card.modifier("+0", 0);
            }
        }
        String action = key != null && key.startsWith(FLIP7_CARD_ACTION_PREFIX)
                ? key.substring(FLIP7_CARD_ACTION_PREFIX.length())
                : "second_chance";
        if ("freeze".equals(action)) {
            return Flip7Card.action("Freeze", "freeze");
        }
        if ("flip_three".equals(action)) {
            return Flip7Card.action("Flip Three", "flip_three");
        }
        return Flip7Card.action("Second Chance", "second_chance");
    }

    private static int calculateFlip7Score(int numberSum, int modifierBonus, int multiplier, int flip7Bonus) {
        return Math.max(0, numberSum * Math.max(1, multiplier) + modifierBonus + flip7Bonus);
    }

    private static List<Flip7Card> createShuffledFlip7Deck() {
        List<Flip7Card> deck = createOrderedFlip7Deck();
        Collections.shuffle(deck, ThreadLocalRandom.current());
        return deck;
    }

    private static List<Flip7Card> createOrderedFlip7Deck() {
        List<Flip7Card> deck = new ArrayList<>();
        deck.add(Flip7Card.number(0));
        for (int value = 1; value <= 12; value++) {
            for (int count = 0; count < value; count++) {
                deck.add(Flip7Card.number(value));
            }
        }
        for (int modifier = 2; modifier <= 10; modifier += 2) {
            deck.add(Flip7Card.modifier("+" + modifier, modifier));
        }
        deck.add(Flip7Card.modifier("x2", null));
        for (int count = 0; count < 3; count++) {
            deck.add(Flip7Card.action("Freeze", "freeze"));
            deck.add(Flip7Card.action("Flip Three", "flip_three"));
            deck.add(Flip7Card.action("Second Chance", "second_chance"));
        }
        return deck;
    }

    private static PetProfileDTO treasureHuntRedeemSkinLocked(long accountId, PetTreasureHuntRedeemSkinDTO request) {
        String skinItemId = request == null ? null : StrUtil.trim(request.getSkinItemId());
        if (StrUtil.isBlank(skinItemId) || !PetItemDefinitions.isSkinItem(skinItemId)) {
            throw new IllegalArgumentException("请选择要兑换的皮肤");
        }
        String fragmentItemId = treasureHuntSkinFragmentItemId(skinItemId);
        if (fragmentItemId == null) {
            throw new IllegalArgumentException("传说皮肤只能通过汪汪寻宝完整抽出");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            if (findInventoryCount(itemMapper, accountId, skinItemId) > 0) {
                throw new IllegalArgumentException("已经拥有该皮肤");
            }
            if (itemMapper.decrementItemIfEnough(accountId, fragmentItemId,
                    TREASURE_HUNT_SKIN_FRAGMENTS_PER_SKIN, now) <= 0) {
                throw new IllegalArgumentException("皮肤碎片不足，10 个对应等级碎片可兑换 1 个皮肤");
            }
            PetItemLedgerMapper ledgerMapper = session.getMapper(PetItemLedgerMapper.class);
            String sourceRef = "treasure_hunt_redeem:" + UUID.randomUUID();
            recordItemLedger(ledgerMapper, accountId, fragmentItemId,
                    TREASURE_HUNT_SKIN_FRAGMENTS_PER_SKIN, ITEM_LEDGER_SPEND,
                    ITEM_LEDGER_SOURCE_TREASURE_HUNT_REDEEM_SKIN, sourceRef, null, now);
            if (itemMapper.addItemIfUnderLimit(accountId, skinItemId, 1, 1, now) <= 0) {
                throw new IllegalArgumentException("已经拥有该皮肤");
            }
            recordItemLedger(ledgerMapper, accountId, skinItemId, 1, ITEM_LEDGER_GAIN,
                    ITEM_LEDGER_SOURCE_TREASURE_HUNT_REDEEM_SKIN, sourceRef, null, now);
            session.commit();
        }
        return profile(accountId);
    }

    private static String treasureHuntSkinFragmentItemId(String skinItemId) {
        if (TREASURE_HUNT_RARE_SKIN_ITEM_IDS.contains(skinItemId)) {
            return PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT;
        }
        if (TREASURE_HUNT_EPIC_SKIN_ITEM_IDS.contains(skinItemId)) {
            return PetItemDefinitions.ITEM_EPIC_SKIN_FRAGMENT;
        }
        return null;
    }

    private static PetProfileDTO grantTreasureHuntBonusSpinsLocked(long accountId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("寻宝次数必须为正整数");
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            ensureAssets(session, accountId);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            if (counterMapper.incrementByIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                    COUNTER_TREASURE_HUNT_BONUS_SPINS, quantity, Integer.MAX_VALUE, now) <= 0) {
                throw new IllegalArgumentException("发放寻宝次数失败");
            }
            session.commit();
        }
        return profile(accountId);
    }

    private static TreasureHuntSettlement settleTreasureHuntSpin(SqlSession session, long accountId, long now) {
        int boneReward = 0;
        int bonusSpinReward = 0;
        List<String> symbols = new ArrayList<>();
        List<String> detailLines = new ArrayList<>();
        List<PetTreasureHuntExtraRewardDTO> extraRewards = new ArrayList<>();

        for (int index = 0; index < TREASURE_HUNT_SLOT_COUNT; index++) {
            TreasureSlotOption rewardOption = pickTreasureRewardOption();
            symbols.add(rewardOption.symbol);
            if (rewardOption.isBones()) {
                boneReward += rewardOption.boneAmount;
                detailLines.add("第 " + (index + 1) + " 格摇出" + rewardOption.label
                        + "，获得骨头币 " + rewardOption.boneAmount + "。");
            } else {
                PetTreasureHuntExtraRewardDTO reward =
                        applyTreasurePrizeReward(session, accountId, now, rewardOption);
                if (reward != null) {
                    extraRewards.add(reward);
                    detailLines.add("第 " + (index + 1) + " 格摇出" + rewardOption.label
                            + "，获得" + treasureRewardLabel(rewardOption, reward) + "。");
                } else {
                    detailLines.add("第 " + (index + 1) + " 格摇出" + rewardOption.label
                            + "，但当前奖励池暂无可发放内容。");
                }
            }
        }

        if (isTreasureTriple(symbols)) {
            bonusSpinReward = grantTreasureHuntBonusSpinsInSession(session, accountId, 1, now);
            detailLines.add("三格都是" + treasureSymbolLabel(symbols.get(0)) + "，额外附赠一次寻宝机会。");
        }

        return new TreasureHuntSettlement(boneReward, extraRewards, bonusSpinReward, symbols, detailLines);
    }

    private static TreasureSlotOption pickTreasureRewardOption() {
        int roll = Math.floorMod(treasureHuntSlotRollSupplier.getAsInt(), TREASURE_HUNT_ROLL_SCALE);
        int cursor = 0;
        for (TreasureSlotOption option : TREASURE_HUNT_SLOT_OPTIONS) {
            cursor += option.probabilityBp;
            if (roll < cursor) {
                return option;
            }
        }
        return TREASURE_HUNT_SLOT_OPTIONS.get(TREASURE_HUNT_SLOT_OPTIONS.size() - 1);
    }

    private static PetTreasureHuntExtraRewardDTO applyTreasurePrizeReward(SqlSession session, long accountId,
                                                                          long now, TreasureSlotOption option) {
        if ("rare_skin_fragment".equals(option.type)) {
            return grantTreasureHuntItem(session, accountId, PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT,
                    "稀有皮肤碎片", option.quantity, TREASURE_HUNT_SKIN_FRAGMENT_MAX_GRANT, now,
                    "rare_skin_fragment");
        }
        if ("epic_skin_fragment".equals(option.type)) {
            return grantTreasureHuntItem(session, accountId, PetItemDefinitions.ITEM_EPIC_SKIN_FRAGMENT,
                    "史诗皮肤碎片", option.quantity, TREASURE_HUNT_SKIN_FRAGMENT_MAX_GRANT, now,
                    "epic_skin_fragment");
        }
        if ("rare_skin".equals(option.type)) {
            return grantTreasureHuntSkinReward(session, accountId, now, option.type,
                    TREASURE_HUNT_RARE_SKIN_ITEM_IDS, PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT,
                    "稀有皮肤碎片");
        }
        if ("epic_skin".equals(option.type)) {
            return grantTreasureHuntSkinReward(session, accountId, now, option.type,
                    TREASURE_HUNT_EPIC_SKIN_ITEM_IDS, PetItemDefinitions.ITEM_EPIC_SKIN_FRAGMENT,
                    "史诗皮肤碎片");
        }
        if ("legend_skin".equals(option.type)) {
            return grantTreasureHuntSkinReward(session, accountId, now, option.type,
                    TREASURE_HUNT_LEGENDARY_SKIN_ITEM_IDS, null, null);
        }

        List<String> pool = "rare_item".equals(option.type)
                ? TREASURE_HUNT_RARE_ITEM_IDS
                : "epic_item".equals(option.type)
                ? TREASURE_HUNT_EPIC_ITEM_IDS
                : TREASURE_HUNT_NORMAL_ITEM_IDS;
        String itemId = selectUnownedItem(session.getMapper(PetItemMapper.class), accountId, pool, MAX_ITEM_COUNT);
        if (itemId == null) {
            return grantTreasureHuntItem(session, accountId, PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT,
                    "稀有皮肤碎片", 1, TREASURE_HUNT_SKIN_FRAGMENT_MAX_GRANT, now, "rare_skin_fragment");
        }
        return grantTreasureHuntItem(session, accountId, itemId, itemId, 1, MAX_ITEM_COUNT, now, "item");
    }

    private static PetTreasureHuntExtraRewardDTO grantTreasureHuntSkinReward(SqlSession session, long accountId,
                                                                             long now, String type,
                                                                             List<String> skinPool,
                                                                             String fallbackFragmentItemId,
                                                                             String fallbackFragmentLabel) {
        String skinItemId = selectUnownedItem(session.getMapper(PetItemMapper.class), accountId, skinPool, 1);
        if (skinItemId != null) {
            return grantTreasureHuntItem(session, accountId, skinItemId, skinItemId, 1, 1, now, type);
        }
        if (fallbackFragmentItemId == null) {
            return null;
        }
        return grantTreasureHuntItem(session, accountId, fallbackFragmentItemId, fallbackFragmentLabel,
                TREASURE_HUNT_SKIN_FRAGMENTS_PER_SKIN, TREASURE_HUNT_SKIN_FRAGMENT_MAX_GRANT, now,
                PetItemDefinitions.ITEM_RARE_SKIN_FRAGMENT.equals(fallbackFragmentItemId)
                        ? "rare_skin_fragment"
                        : "epic_skin_fragment");
    }

    private static int grantTreasureHuntBonusSpinsInSession(SqlSession session, long accountId, int quantity,
                                                            long now) {
        if (quantity <= 0) {
            return 0;
        }
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        if (counterMapper.incrementByIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                COUNTER_TREASURE_HUNT_BONUS_SPINS, quantity, Integer.MAX_VALUE, now) <= 0) {
            throw new IllegalArgumentException("发放寻宝次数失败");
        }
        return quantity;
    }

    private static PetTreasureHuntExtraRewardDTO grantTreasureHuntItem(SqlSession session, long accountId,
                                                                       String itemId, String label, int quantity,
                                                                       int maxCount, long now, String type) {
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        int updated = PetItemDefinitions.ITEM_SKIN_TICKET.equals(itemId)
                ? itemMapper.addItem(accountId, itemId, quantity, now)
                : itemMapper.addItemIfUnderLimit(accountId, itemId, quantity, maxCount, now);
        if (updated <= 0) {
            return null;
        }
        recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, quantity,
                ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_TREASURE_HUNT, "treasure_hunt:" + UUID.randomUUID(),
                null, now);
        return new PetTreasureHuntExtraRewardDTO(type, itemId, label, quantity);
    }

    private static String selectUnownedItem(PetItemMapper mapper, long accountId, List<String> itemIds, int maxCount) {
        List<String> candidates = new ArrayList<>();
        for (String itemId : itemIds) {
            if (findInventoryCount(mapper, accountId, itemId) < maxCount) {
                candidates.add(itemId);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(Math.floorMod(treasureHuntItemIndexSupplier.getAsInt(), candidates.size()));
    }

    private static int findInventoryCount(PetItemMapper mapper, long accountId, String itemId) {
        PetItemRecord item = mapper.findByAccountIdAndItemId(accountId, itemId);
        return item == null ? 0 : Math.max(0, item.getCount());
    }

    private static boolean isTreasureTriple(List<String> symbols) {
        if (symbols.size() != TREASURE_HUNT_SLOT_COUNT) {
            return false;
        }
        String first = symbols.get(0);
        for (String symbol : symbols) {
            if (!Objects.equals(first, symbol)) {
                return false;
            }
        }
        return true;
    }

    private static String treasureRewardLabel(TreasureSlotOption option, PetTreasureHuntExtraRewardDTO reward) {
        if (reward == null) {
            return option.label;
        }
        int quantity = Math.max(1, reward.getQuantity());
        if (option.type.endsWith("_skin") && reward.getItemId() != null) {
            return option.label;
        }
        String label = StrUtil.blankToDefault(reward.getLabel(), option.label);
        return quantity > 1 ? label + " ×" + quantity : label;
    }

    private static String treasureSymbolLabel(String symbol) {
        for (TreasureSlotOption option : TREASURE_HUNT_SLOT_OPTIONS) {
            if (Objects.equals(option.symbol, symbol)) {
                return option.label;
            }
        }
        return symbol;
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
                counterMapper.incrementIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                        miniGameWinCounter(game), Integer.MAX_VALUE, now);
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

    private static void recordTacitQuizSameAnswerLocked(long accountId) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            counterMapper.incrementIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                    COUNTER_TACIT_QUIZ_SAME_ANSWERS, Integer.MAX_VALUE, now);
            session.commit();
        }
    }

    private static void applyMiniGameRoomBonusLocked(List<Long> accountIds) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            Long tagOwnerAccountId = null;
            for (Long accountId : accountIds) {
                ensureAssets(session, accountId);
                if (tagOwnerAccountId == null
                        && findCollectionCount(collectionMapper, accountId, EASTER_VISIT_DOG_TAG_COLLECTION_ID) > 0) {
                    tagOwnerAccountId = accountId;
                }
            }
            if (tagOwnerAccountId != null && collectionMapper.decrementCollectionIfEnough(
                    tagOwnerAccountId, EASTER_VISIT_DOG_TAG_COLLECTION_ID, 1, now) > 0) {
                PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
                for (Long accountId : accountIds) {
                    assetsMapper.addBones(accountId, EASTER_VISIT_DOG_TAG_BONES, now);
                }
            }
            session.commit();
        }
    }

    private static List<Long> normalizeRoomBonusAccountIds(List<Long> accountIds) {
        List<Long> normalizedAccountIds = new ArrayList<>();
        if (accountIds == null) {
            return normalizedAccountIds;
        }
        Set<Long> seen = new HashSet<>();
        for (Long accountId : accountIds) {
            if (accountId == null || accountId <= 0L || !seen.add(accountId)) {
                continue;
            }
            normalizedAccountIds.add(accountId);
        }
        return normalizedAccountIds;
    }

    private static void runWithAccountLocks(List<Long> accountIds, int index, Runnable action) {
        if (index >= accountIds.size()) {
            action.run();
            return;
        }
        synchronized (accountLock(accountIds.get(index))) {
            runWithAccountLocks(accountIds, index + 1, action);
        }
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
        refreshExpiredAccountEnergy(session.getMapper(PetAssetsMapper.class), accountId,
                effectiveEnergyLimit(session, accountId, assets.getEnergyLimit()), today, now);
        PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);

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
                && dogMapper.updateCareStats(dog.getId(), accountId, bond, now) <= 0) {
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
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            int energyLimit = effectiveEnergyLimit(session, accountId, assets.getEnergyLimit());
            if (refreshExpiredAccountEnergy(assetsMapper, accountId, energyLimit, today, now)) {
                assets = assetsMapper.findByAccountId(accountId);
            }
            PetDogRecord dog = dogMapper.findByIdAndOwner(normalizedDogId, accountId);
            if (dog == null) {
                throw new IllegalArgumentException("狗狗不存在");
            }
            if (assets.getBones() < bonesCost) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (assets.getEnergy() < energyCost) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            if (assetsMapper.decrementBonesIfEnough(accountId, bonesCost, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (assetsMapper.decrementEnergyIfEnough(accountId, energyCost, now) <= 0) {
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
            PetAssetsRecord assets = ensureAssets(session, accountId);
            List<PetDogDTO> dogs = new ArrayList<>();
            for (PetDogRecord dog : dogMapper.listByOwner(accountId)) {
                dogs.add(toDTO(dog));
            }
            List<String> activeDogIds = request.getActive() == null
                    ? Collections.singletonList(dogId)
                    : updateActiveDogIds(resolveActiveDogIds(assets.getCompanionDogId(), dogs),
                    dogId, request.getActive());
            session.getMapper(PetAssetsMapper.class).updateCompanionDogId(accountId,
                    serializeActiveDogIds(activeDogIds), now);
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
        if (DAILY_SKIN_SHOP_ITEM_IDS.contains(itemId)) {
            return buyDailySkinItem(accountId, itemId, quantity);
        }
        throw new IllegalArgumentException("暂不支持该商店商品");
    }

    private static PetProfileDTO shopRefreshLocked(long accountId) {
        long now = System.currentTimeMillis();
        long periodStartAt = currentShopShelfPeriodStartAt(now);
        String counterDate = shopShelfCounterDate(periodStartAt);
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            int usedRefreshes = findDailyCounterValue(counterMapper, accountId, counterDate,
                    COUNTER_SHOP_SHELF_PAID_REFRESH);
            if (usedRefreshes >= SHOP_SHELF_MAX_PAID_REFRESHES) {
                throw new IllegalArgumentException("本轮商店刷新次数已达上限");
            }
            int cost = SHOP_SHELF_PAID_REFRESH_COSTS.get(usedRefreshes);
            if (assets.getBones() < cost) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (assetsMapper.decrementBonesIfEnough(accountId, cost, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (counterMapper.incrementIfUnderLimit(accountId, counterDate,
                    COUNTER_SHOP_SHELF_PAID_REFRESH, SHOP_SHELF_MAX_PAID_REFRESHES, now) <= 0) {
                throw new IllegalArgumentException("本轮商店刷新次数已达上限");
            }
            session.commit();
        }
        return profileLocked(accountId);
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, quantity,
                    ITEM_LEDGER_SPEND, ITEM_LEDGER_SOURCE_SELL_ITEM, null, null, now);
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
            PetShopStatusDTO shopStatus = buildShopStatus(counterMapper, accountId, now);
            if (!shopStatus.getNormalItemIds().contains(itemId)) {
                throw new IllegalArgumentException("当前货架未出售该普通道具");
            }
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, quantity,
                    ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_SHOP_BUY_NORMAL, null, null, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyDailyRareItem(long accountId, String itemId, int quantity) {
        LocalDate today = LocalDate.now();
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
            PetShopStatusDTO shopStatus = buildShopStatus(counterMapper, accountId, now);
            if (!itemId.equals(shopStatus.getRareItemId())) {
                throw new IllegalArgumentException("当前货架未出售该稀有道具");
            }
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
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, quantity,
                    ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_SHOP_BUY_DAILY_RARE, todayText, null, now);
            session.commit();
        }

        return profile(accountId);
    }

    private static PetProfileDTO buyDailySkinItem(long accountId, String itemId, int quantity) {
        LocalDate today = LocalDate.now();
        if (quantity > DAILY_SKIN_ITEM_BUY_LIMIT) {
            throw new IllegalArgumentException("今日皮肤购买次数已达上限");
        }

        long now = System.currentTimeMillis();
        String todayText = today.toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
            PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
            PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            PetShopStatusDTO shopStatus = buildShopStatus(counterMapper, accountId, now);
            if (!itemId.equals(shopStatus.getDailySkinItemId())) {
                throw new IllegalArgumentException("今日未出售该皮肤");
            }
            PetItemRecord item = itemMapper.findByAccountIdAndItemId(accountId, itemId);
            int currentCount = item == null ? 0 : item.getCount();
            if ((long) currentCount + quantity > 1) {
                throw new IllegalArgumentException("该皮肤已拥有");
            }

            int price = SHOP_DAILY_SKIN_ITEM_PRICE * quantity;
            if (assets.getBones() < price) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (counterMapper.incrementByIfUnderLimit(accountId, todayText, DAILY_COUNTER_SHOP_DAILY_SKIN_ITEM_BUY,
                    quantity, DAILY_SKIN_ITEM_BUY_LIMIT, now) <= 0) {
                throw new IllegalArgumentException("今日皮肤购买次数已达上限");
            }
            if (assetsMapper.decrementBonesIfEnough(accountId, price, now) <= 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (itemMapper.addItemIfUnderLimit(accountId, itemId, quantity, 1, now) <= 0) {
                throw new IllegalArgumentException("该皮肤已拥有");
            }
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, quantity,
                    ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_SHOP_BUY_DAILY_SKIN, todayText, null, now);
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
            boolean constructionSiteCollectionCompleted = hasCompletedCollectionSet(
                    session.getMapper(PetCollectionMapper.class), accountId,
                    CONSTRUCTION_SITE_COLLECTION_ITEM_IDS);
            List<String> rewards = new ArrayList<>(quantity);
            for (int i = 0; i < quantity; i++) {
                String rewardItemId = rollLuckyBagItem(itemCounts, constructionSiteCollectionCompleted);
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
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, rewardItemId, 1,
                        ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_SHOP_BUY_LUCKY_BAG, null, null, now);
            }
            session.commit();
        }

        return profile(accountId);
    }

    private static boolean settleEndedExploresAsChests(long accountId,
                                                       PetDogMapper dogMapper,
                                                       PetExploreChestMapper chestMapper,
                                                       PetItemLedgerMapper ledgerMapper,
                                                       PetTrainingMapper trainingMapper,
                                                       PetDailyCounterMapper counterMapper,
                                                       List<PetDogRecord> dogs,
                                                       long now) {
        boolean settled = false;
        for (PetDogRecord dog : dogs) {
            if (!"exploring".equals(dog.getStatus())) {
                continue;
            }
            String location = StrUtil.trim(dog.getExploreLocation());
            String chestItemId = exploreChestItemId(location);
            if (chestItemId == null) {
                continue;
            }
            if (dog.getExploreEndsAt() == null || dog.getExploreEndsAt() > now) {
                continue;
            }
            if (chestMapper.countAvailableByAccountIdAndChestItemId(accountId, chestItemId) >= MAX_EXPLORE_CHEST_COUNT) {
                throw new IllegalArgumentException(exploreChestFullError(location));
            }
            PetExploreChestRecord chest = buildExploreChest(accountId, chestItemId, location, dog, now);
            chestMapper.insert(chest);
            recordItemLedger(ledgerMapper, accountId, chestItemId, 1, ITEM_LEDGER_GAIN,
                    ITEM_LEDGER_SOURCE_EXPLORE_RETURN_CHEST, chest.getId(), null, now);
            recordExploreCompletion(counterMapper, accountId, location, now);
            grantOutingBondIfAvailable(counterMapper, dogMapper, accountId,
                    LocalDate.now().toString(), dog, now);
            grantFirstExploreFreeLearnIfEligible(trainingMapper, accountId, inferExploreDurationHours(dog), now);
            if (dogMapper.openExplore(dog.getId(), accountId, now) <= 0) {
                throw new IllegalArgumentException("探险结算失败，请刷新后重试");
            }
            settled = true;
        }
        return settled;
    }

    private static boolean migrateLegacyExploreChests(long accountId,
                                                      PetItemMapper itemMapper,
                                                      PetExploreChestMapper chestMapper,
                                                      PetItemLedgerMapper ledgerMapper,
                                                      long now) {
        boolean migrated = false;
        for (PetItemRecord item : itemMapper.listPositiveByAccountId(accountId)) {
            String location = exploreLocationByChestItemId(item.getItemId());
            if (location == null || item.getCount() <= 0) {
                continue;
            }
            int availableCount = chestMapper.countAvailableByAccountIdAndChestItemId(accountId, item.getItemId());
            if (availableCount + item.getCount() > MAX_EXPLORE_CHEST_COUNT) {
                throw new IllegalArgumentException(exploreChestFullError(location));
            }
            for (int i = 0; i < item.getCount(); i++) {
                PetExploreChestRecord chest = buildLegacyExploreChest(accountId, item.getItemId(), location, now + i);
                chestMapper.insert(chest);
                recordItemLedger(ledgerMapper, accountId, item.getItemId(), 1, ITEM_LEDGER_GAIN,
                        ITEM_LEDGER_SOURCE_LEGACY_CHEST_MIGRATION, chest.getId(), null, now + i);
            }
            if (itemMapper.decrementItemIfEnough(accountId, item.getItemId(), item.getCount(), now) <= 0) {
                throw new IllegalArgumentException("箱子库存迁移失败，请刷新后重试");
            }
            migrated = true;
        }
        return migrated;
    }

    private static int exploreChestCount(PetExploreChestMapper chestMapper, PetItemMapper itemMapper,
                                         long accountId, String chestItemId) {
        int count = chestMapper.countAvailableByAccountIdAndChestItemId(accountId, chestItemId);
        PetItemRecord item = itemMapper.findByAccountIdAndItemId(accountId, chestItemId);
        return count + (item == null ? 0 : Math.max(0, item.getCount()));
    }

    private static PetExploreChestRecord buildExploreChest(long accountId, String chestItemId,
                                                           String location, PetDogRecord dog, long now) {
        return PetExploreChestRecord.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .chestItemId(chestItemId)
                .location(location)
                .sourceDogId(dog.getId())
                .sourceDogName(dog.getName())
                .sourceDogBreed(dog.getBreed())
                .durationHours(inferExploreDurationHours(dog))
                .skillSnapshotId(dog.getExploreSkillSnapshotId())
                .skillSnapshotLevel(dog.getExploreSkillSnapshotLevel())
                .skillSnapshotDefinitionVersion(dog.getExploreSkillSnapshotVersion())
                .status(EXPLORE_CHEST_STATUS_AVAILABLE)
                .createdAt(now)
                .build();
    }

    private static PetExploreChestRecord buildLegacyExploreChest(long accountId, String chestItemId,
                                                                 String location, long now) {
        return PetExploreChestRecord.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .chestItemId(chestItemId)
                .location(location)
                .sourceDogId(null)
                .sourceDogName("旧箱子")
                .sourceDogBreed(null)
                .durationHours(EXPLORE_ONE_HOUR)
                .status(EXPLORE_CHEST_STATUS_AVAILABLE)
                .createdAt(now)
                .build();
    }

    private static PetDogRecord exploreSnapshotDog(PetExploreChestRecord chest) {
        if (chest == null || StrUtil.isBlank(chest.getSourceDogId())) {
            return null;
        }
        PetDogRecord dog = new PetDogRecord();
        dog.setId(chest.getSourceDogId());
        dog.setOwnerId(chest.getAccountId());
        dog.setName(StrUtil.blankToDefault(chest.getSourceDogName(), "探险狗狗"));
        dog.setBreed(chest.getSourceDogBreed());
        dog.setExploreSkillSnapshotId(chest.getSkillSnapshotId());
        dog.setExploreSkillSnapshotLevel(chest.getSkillSnapshotLevel());
        dog.setExploreSkillSnapshotVersion(chest.getSkillSnapshotDefinitionVersion());
        return dog;
    }

    private static void recordItemLedger(PetItemLedgerMapper mapper, long accountId, String itemId, int quantity,
                                         String direction, String source, String sourceRef,
                                         String metadataJson, long now) {
        PetItemLedgerRecord record = new PetItemLedgerRecord();
        record.setId(UUID.randomUUID().toString());
        record.setAccountId(accountId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setDirection(direction);
        record.setSource(source);
        record.setSourceRef(sourceRef);
        record.setMetadataJson(metadataJson);
        record.setCreatedAt(now);
        mapper.insert(record);
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

    private static void applyExploreEnergyTraining(PetAssetsMapper assetsMapper, long accountId, PetDogRecord dog,
                                                   int energyLimit, List<PetExploreRewardDTO> rewards, long now) {
        int refundPercent = exploreSnapshotEffect(dog, TRAINING_SKILL_ENERGY);
        if (refundPercent <= 0 || nextExploreRoll() >= refundPercent) {
            return;
        }
        if (assetsMapper.addEnergyIfUnderLimit(accountId, 1, energyLimit, now) > 0) {
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
                || EXPLORE_LOCATION_CREEK.equals(location)
                || EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)
                || EXPLORE_LOCATION_OLD_LIBRARY.equals(location)
                || EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)
                || EXPLORE_LOCATION_MYSTERY_CAVE.equals(location);
    }

    private static void ensureExploreLocationUnlocked(SqlSession session, long accountId, String location) {
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            int drawGuessWins = findLifetimeCounterValue(counterMapper, accountId, miniGameWinCounter(Game.DRAW_GUESS));
            int tacitQuizSameAnswers = findTacitQuizSameAnswers(counterMapper, accountId);
            if (drawGuessWins < CREEK_UNLOCK_DRAW_GUESS_WINS
                    && tacitQuizSameAnswers < CREEK_UNLOCK_TACIT_QUIZ_SAME_ANSWERS) {
                throw new IllegalArgumentException("你画我猜胜利 10 次或默契问答答案相同 50 次后才能进入小溪");
            }
            return;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            int quickQuizWins = findLifetimeCounterValue(counterMapper, accountId,
                    miniGameWinCounter(Game.QUICK_QUIZ));
            if (quickQuizWins < CONSTRUCTION_SITE_UNLOCK_QUICK_QUIZ_WINS) {
                throw new IllegalArgumentException("快问快答累计胜利 10 局后才能进入废弃工地");
            }
            return;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            int oldLibraryWins = findLifetimeCounterValue(counterMapper, accountId, miniGameWinCounter(Game.GOBANG))
                    + findLifetimeCounterValue(counterMapper, accountId, miniGameWinCounter(Game.TURTLE_SOUP));
            if (oldLibraryWins < OLD_LIBRARY_UNLOCK_LIBRARY_WINS) {
                throw new IllegalArgumentException("五子棋/海龟汤累计胜利 10 局后才能进入旧书馆");
            }
            return;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
            boolean mysteryCaveCompleted = findCollectionCount(collectionMapper, accountId,
                    MYSTERY_CAVE_COMPLETED_COLLECTION_ID) > 0;
            int oldLibraryCompletions = findLifetimeCounterValue(counterMapper, accountId,
                    exploreCompleteCounter(EXPLORE_LOCATION_OLD_LIBRARY));
            if (!mysteryCaveCompleted || oldLibraryCompletions < SNOW_MOUNTAIN_UNLOCK_OLD_LIBRARY_COMPLETIONS) {
                throw new IllegalArgumentException("完成神秘洞穴并完成旧书馆探险 3 次后才能进入雪山");
            }
        }
    }

    private static String exploreChestItemId(String location) {
        if (EXPLORE_LOCATION_BACK_HILL.equals(location)) {
            return ITEM_BACK_HILL_CHEST;
        }
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            return ITEM_CREEK_CHEST;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            return ITEM_CONSTRUCTION_SITE_CHEST;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            return ITEM_OLD_LIBRARY_CHEST;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            return ITEM_SNOW_MOUNTAIN_CHEST;
        }
        return null;
    }

    private static String exploreLocationByChestItemId(String itemId) {
        if (ITEM_BACK_HILL_CHEST.equals(itemId)) {
            return EXPLORE_LOCATION_BACK_HILL;
        }
        if (ITEM_CREEK_CHEST.equals(itemId)) {
            return EXPLORE_LOCATION_CREEK;
        }
        if (ITEM_CONSTRUCTION_SITE_CHEST.equals(itemId)) {
            return EXPLORE_LOCATION_CONSTRUCTION_SITE;
        }
        if (ITEM_OLD_LIBRARY_CHEST.equals(itemId)) {
            return EXPLORE_LOCATION_OLD_LIBRARY;
        }
        if (ITEM_SNOW_MOUNTAIN_CHEST.equals(itemId)) {
            return EXPLORE_LOCATION_SNOW_MOUNTAIN;
        }
        return null;
    }

    private static String exploreChestFullError(String location) {
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            return CREEK_CHEST_FULL_ERROR;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            return CONSTRUCTION_SITE_CHEST_FULL_ERROR;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            return OLD_LIBRARY_CHEST_FULL_ERROR;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            return SNOW_MOUNTAIN_CHEST_FULL_ERROR;
        }
        return BACK_HILL_CHEST_FULL_ERROR;
    }

    private static List<String> exploreNormalItemIds(String location) {
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            return CREEK_NORMAL_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            return CONSTRUCTION_SITE_NORMAL_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            return OLD_LIBRARY_NORMAL_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            return Collections.emptyList();
        }
        return BACK_HILL_NORMAL_ITEM_IDS;
    }

    private static List<String> exploreRareItemIds(String location) {
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            return CREEK_RARE_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            return CONSTRUCTION_SITE_RARE_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            return OLD_LIBRARY_RARE_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            return Collections.emptyList();
        }
        return BACK_HILL_RARE_ITEM_IDS;
    }

    private static List<String> exploreCollectionItemIds(String location) {
        if (EXPLORE_LOCATION_CREEK.equals(location)) {
            return CREEK_COLLECTION_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_CONSTRUCTION_SITE.equals(location)) {
            return CONSTRUCTION_SITE_COLLECTION_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_OLD_LIBRARY.equals(location)) {
            return OLD_LIBRARY_COLLECTION_ITEM_IDS;
        }
        if (EXPLORE_LOCATION_SNOW_MOUNTAIN.equals(location)) {
            return SNOW_MOUNTAIN_COLLECTION_ITEM_IDS;
        }
        return BACK_HILL_COLLECTION_ITEM_IDS;
    }

    private static void recordExploreCompletion(PetDailyCounterMapper mapper, long accountId,
                                                String location, long now) {
        if (exploreChestItemId(location) == null) {
            return;
        }
        mapper.incrementIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                exploreCompleteCounter(location), Integer.MAX_VALUE, now);
    }

    private static String exploreCompleteCounter(String location) {
        return COUNTER_EXPLORE_COMPLETE_PREFIX + location;
    }

    private static String miniGameWinCounter(Game game) {
        return COUNTER_MINI_GAME_WIN_PREFIX
                + (game == null ? "unknown" : game.name().toLowerCase(Locale.ROOT));
    }

    private static int findTacitQuizSameAnswers(PetDailyCounterMapper mapper, long accountId) {
        int legacyMatchedRounds = findLifetimeCounterValue(mapper, accountId, miniGameWinCounter(Game.TACIT_QUIZ));
        return findTacitQuizSameAnswers(mapper, accountId, legacyMatchedRounds);
    }

    private static int findTacitQuizSameAnswers(PetDailyCounterMapper mapper, long accountId, int legacyMatchedRounds) {
        int sameAnswers = findLifetimeCounterValue(mapper, accountId, COUNTER_TACIT_QUIZ_SAME_ANSWERS);
        return Math.max(sameAnswers, legacyMatchedRounds);
    }

    private static int findLifetimeCounterValue(PetDailyCounterMapper mapper, long accountId, String counter) {
        return findDailyCounterValue(mapper, accountId, COUNTER_DATE_LIFETIME, counter);
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
                                          PetDogRecord dog, String location,
                                          List<PetExploreRewardDTO> rewards, String today, long now) {
        PetAssetsMapper assetsMapper = session.getMapper(PetAssetsMapper.class);
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        PetItemLedgerMapper ledgerMapper = session.getMapper(PetItemLedgerMapper.class);
        Map<String, Integer> itemCounts = exploreItemCounts(itemMapper, accountId);
        int collectionBonus = exploreSnapshotEffect(dog, TRAINING_SKILL_COLLECTION);
        int treasureBonus = exploreSnapshotEffect(dog, TRAINING_SKILL_TREASURE);
        for (int i = 0; i < exploreRollCount(durationHours); i++) {
            int roll = nextExploreRoll();
            if (roll < 50) {
                applyExploreItemReward(assetsMapper, counterMapper, itemMapper, ledgerMapper, accountId,
                        exploreNormalItemIds(location), itemCounts, rewards, today, now);
            } else if (roll < 58) {
                applyExploreItemReward(assetsMapper, counterMapper, itemMapper, ledgerMapper, accountId,
                        exploreRareItemIds(location), itemCounts, rewards, today, now);
            } else if (roll < 78) {
                applyExploreCollectionReward(session, accountId, location, rewards, now);
            } else if (roll < 80) {
                applyExploreEasterEventReward(session, accountId, dog, assetsMapper, rewards, now);
            } else if (roll < 80 + collectionBonus) {
                applyExploreCollectionReward(session, accountId, location, rewards, now);
            } else if (roll < 80 + collectionBonus + treasureBonus) {
                applyExploreTreasureMapReward(session, accountId, rewards, now);
            } else {
                addExploreBones(assetsMapper, accountId, EXPLORE_ROLL_BONES, rewards, now);
            }
        }
    }

    private static void applyExploreCollectionReward(SqlSession session, long accountId,
                                                     String location,
                                                     List<PetExploreRewardDTO> rewards, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        String itemId = pickExploreCollectionItem(collectionMapper.listByAccountId(accountId),
                exploreCollectionItemIds(location));
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
                                                      PetDogRecord dog,
                                                      PetAssetsMapper assetsMapper,
                                                      List<PetExploreRewardDTO> rewards, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        List<String> eventIds = availableExploreEasterEventIds(collectionMapper, counterMapper, accountId, dog);
        if (eventIds.isEmpty()) {
            addExploreBones(assetsMapper, accountId, EXPLORE_EASTER_EVENT_OVERFLOW_BONES, rewards, now);
            return;
        }

        String eventId = eventIds.get(Math.floorMod(nextExploreEasterEventIndex(), eventIds.size()));
        if (EASTER_OLD_TENNIS_COLLECTION_ID.equals(eventId)) {
            if (dog == null || counterMapper.incrementIfUnderLimit(accountId, COUNTER_DATE_LIFETIME,
                    EASTER_OLD_TENNIS_PENDING_PREFIX + dog.getId(), 1, now) <= 0) {
                addExploreBones(assetsMapper, accountId, EXPLORE_EASTER_EVENT_OVERFLOW_BONES, rewards, now);
                return;
            }
            rewards.add(new PetExploreRewardDTO("easter_event", EASTER_OLD_TENNIS_COLLECTION_ID, 1));
            return;
        }
        collectionMapper.addCollection(accountId, eventId, now);
        rewards.add(new PetExploreRewardDTO("collection", eventId, 1));
    }

    private static List<String> availableExploreEasterEventIds(PetCollectionMapper collectionMapper,
                                                               PetDailyCounterMapper counterMapper,
                                                               long accountId,
                                                               PetDogRecord dog) {
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
        if (!isCollectionDiscovered(collectionMapper, accountId, EASTER_VISIT_DOG_TAG_COLLECTION_ID)) {
            eventIds.add(EASTER_VISIT_DOG_TAG_COLLECTION_ID);
        }
        if (dog != null
                && !isCollectionDiscovered(collectionMapper, accountId, EASTER_OLD_TENNIS_COLLECTION_ID)
                && firstPendingOldTennisCounter(counterMapper, accountId) == null) {
            eventIds.add(EASTER_OLD_TENNIS_COLLECTION_ID);
        }
        return eventIds;
    }

    private static boolean isCollectionDiscovered(PetCollectionMapper collectionMapper, long accountId, String itemId) {
        return collectionMapper.countDiscovered(accountId, itemId) > 0;
    }

    private static String pickExploreCollectionItem(List<PetCollectionRecord> collections,
                                                    List<String> collectionItemIds) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetCollectionRecord collection : collections) {
            counts.put(collection.getItemId(), collection.getCount());
        }
        String selected = collectionItemIds.get(0);
        int selectedCount = Integer.MAX_VALUE;
        for (String itemId : collectionItemIds) {
            int count = counts.getOrDefault(itemId, 0);
            if (count < selectedCount) {
                selected = itemId;
                selectedCount = count;
            }
        }
        return selected;
    }

    private static void applyExploreItemReward(PetAssetsMapper assetsMapper, PetDailyCounterMapper counterMapper,
                                               PetItemMapper itemMapper, PetItemLedgerMapper ledgerMapper,
                                               long accountId, List<String> pool,
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
        recordItemLedger(ledgerMapper, accountId, itemId, 1, ITEM_LEDGER_GAIN,
                ITEM_LEDGER_SOURCE_EXPLORE_REWARD, null, null, now);
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
            if (LUCKY_BAG_ITEM_IDS.contains(item.getItemId())) {
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

    private static String rollLuckyBagItem(Map<String, Integer> itemCounts,
                                           boolean constructionSiteCollectionCompleted) {
        int rarityRoll = nextLuckyBagRarityRoll();
        List<String> pool;
        if (rarityRoll < 70) {
            pool = LUCKY_BAG_NORMAL_ITEM_IDS;
        } else if (rarityRoll < 95) {
            pool = LUCKY_BAG_RARE_ITEM_IDS;
        } else {
            pool = LUCKY_BAG_EPIC_ITEM_IDS;
        }

        String itemId = pickAvailableLuckyBagItem(pool, itemCounts, constructionSiteCollectionCompleted);
        if (itemId != null) {
            return itemId;
        }
        return pickAvailableLuckyBagItem(LUCKY_BAG_ITEM_IDS, itemCounts, constructionSiteCollectionCompleted);
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
        return availableItems.get(nextLuckyBagItemIndex(availableItems.size()));
    }

    private static String pickAvailableLuckyBagItem(List<String> pool, Map<String, Integer> itemCounts,
                                                    boolean constructionSiteCollectionCompleted) {
        if (!constructionSiteCollectionCompleted) {
            return pickAvailableLuckyBagItem(pool, itemCounts);
        }
        return pickAvailableLuckyBagItem(applyConstructionSiteLuckyBagWeight(pool), itemCounts);
    }

    private static List<String> applyConstructionSiteLuckyBagWeight(List<String> pool) {
        List<String> weightedPool = new ArrayList<>(pool.size() + CONSTRUCTION_SITE_LUCKY_BAG_ITEM_IDS.size());
        for (String itemId : pool) {
            weightedPool.add(itemId);
            if (CONSTRUCTION_SITE_LUCKY_BAG_ITEM_IDS.contains(itemId)) {
                weightedPool.add(itemId);
            }
        }
        return weightedPool;
    }

    private static int nextLuckyBagRarityRoll() {
        return Math.floorMod(luckyBagRarityRollSupplier.getAsInt(), 100);
    }

    private static int nextLuckyBagItemIndex(int size) {
        return Math.floorMod(luckyBagItemIndexSupplier.getAsInt(), size);
    }

    private static int nextShibaCheckinRoll() {
        return Math.floorMod(shibaCheckinRollSupplier.getAsInt(), 100);
    }

    private static PetProfileDTO buySlotLocked(long accountId) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
            PetAssetsRecord assets = ensureAssets(session, accountId);
            if (mapper.buySecondDogSlotIfAffordable(accountId, SECOND_DOG_SLOT_PRICE, now) <= 0) {
                if (assets.getDogSlots() >= MAX_DOG_SLOTS) {
                    throw new IllegalArgumentException("携带栏已达上限");
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
            maybeUnlockShibaFromCheckin(session, accountId, previousTotalCheckins + 1, true, now);
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
            PetAssetsRecord assets = ensureAssets(session, accountId);
            LocalDate makeupAvailableSince = LocalDate.parse(
                    accountCheckinStartDate(session, accountId, assets, today.toString()));
            if (checkinDate.isBefore(makeupAvailableSince)) {
                throw new IllegalArgumentException("只能补签账号可签到日期之后的漏签日期");
            }
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
            maybeUnlockShibaFromCheckin(session, accountId, previousTotalCheckins + 1, false, now);
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

    private static void maybeUnlockShibaFromCheckin(SqlSession session, long accountId, int totalCheckins,
                                                    boolean actualCheckin, long now) {
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        if (findCollectionCount(collectionMapper, accountId, SHIBA_UNLOCK_COLLECTION_ID) > 0) {
            return;
        }
        if (totalCheckins >= SHIBA_CHECKIN_PITY_COUNT
                || (actualCheckin && nextShibaCheckinRoll() < SHIBA_DAILY_CHECKIN_ROLL_THRESHOLD)) {
            collectionMapper.addCollection(accountId, SHIBA_UNLOCK_COLLECTION_ID, now);
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
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        String itemId = pickAvailableSkinItem(itemMapper, accountId);
        int overflowBones = 0;

        if (itemId != null && itemMapper.addItemIfUnderLimit(accountId, itemId, 1, 1, now) > 0) {
            recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, 1,
                    ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_CHECKIN_REWARD,
                    "milestone:" + milestoneIndex, null, now);
        } else {
            itemId = pickAvailableLuckyBagItem(LUCKY_BAG_EPIC_ITEM_IDS, luckyBagItemCounts(itemMapper, accountId));
            if (itemId == null || itemMapper.addItemIfUnderLimit(accountId, itemId, 1, MAX_ITEM_COUNT, now) <= 0) {
                itemId = null;
                overflowBones = CHECKIN_MILESTONE_EPIC_ITEM_OVERFLOW_BONES;
                session.getMapper(PetAssetsMapper.class).addBones(accountId, overflowBones, now);
            } else {
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, 1,
                        ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_CHECKIN_REWARD,
                        "milestone:" + milestoneIndex, null, now);
            }
        }

        return new PetCheckinMilestoneRewardDTO(milestoneIndex, null, itemId, overflowBones);
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
                recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, itemId, 1,
                        ITEM_LEDGER_GAIN, ITEM_LEDGER_SOURCE_CHECKIN_REWARD, "cycle_day:7", null, now);
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

    private static String pickAvailableSkinItem(PetItemMapper itemMapper, long accountId) {
        Map<String, Integer> counts = new HashMap<>();
        for (PetItemRecord item : itemMapper.listPositiveByAccountId(accountId)) {
            if (SKIN_ITEM_IDS.contains(item.getItemId())) {
                counts.put(item.getItemId(), item.getCount());
            }
        }
        List<String> availableItems = new ArrayList<>();
        for (String itemId : SKIN_ITEM_IDS) {
            if (counts.getOrDefault(itemId, 0) < 1) {
                availableItems.add(itemId);
            }
        }
        if (availableItems.isEmpty()) {
            return null;
        }
        return availableItems.get(nextLuckyBagItemIndex(availableItems.size()));
    }

    private static int checkinMilestoneRemaining(int totalCheckins) {
        int normalizedTotal = Math.max(0, totalCheckins);
        int remainder = normalizedTotal % CHECKIN_MILESTONE_INTERVAL;
        return remainder == 0 ? CHECKIN_MILESTONE_INTERVAL : CHECKIN_MILESTONE_INTERVAL - remainder;
    }

    private static PetDogRecord resolveCompanionDog(String savedDogId, List<PetDogRecord> dogs) {
        String primaryDogId = primaryActiveDogId(savedDogId);
        if (StrUtil.isNotBlank(primaryDogId)) {
            for (PetDogRecord dog : dogs) {
                if (primaryDogId.equals(dog.getId())) {
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
                .energy(DEFAULT_ENERGY_LIMIT)
                .energyDate(LocalDate.now().toString())
                .energyLimit(DEFAULT_ENERGY_LIMIT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mapper.insert(assets);
        return assets;
    }

    private static String petAccountStartDate(PetAssetsRecord assets, String fallbackDate) {
        if (assets == null || assets.getCreatedAt() <= 0) {
            return fallbackDate;
        }
        return Instant.ofEpochMilli(assets.getCreatedAt())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString();
    }

    private static String accountCheckinStartDate(SqlSession session, long accountId, PetAssetsRecord assets,
                                                  String fallbackDate) {
        Account account = session.getMapper(AccountMapper.class).findById(accountId);
        if (account != null && account.getCreatedAt() > 0) {
            return Instant.ofEpochMilli(account.getCreatedAt())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString();
        }
        return petAccountStartDate(assets, fallbackDate);
    }

    private static boolean refreshExpiredAccountEnergy(PetAssetsMapper assetsMapper, long accountId, int energyLimit,
                                                       String today, long now) {
        return assetsMapper.refreshExpiredEnergy(accountId, energyLimit, today, now) > 0;
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
                .energy(DEFAULT_ENERGY_LIMIT)
                .energyDate(LocalDate.now().toString())
                .energyLimit(DEFAULT_ENERGY_LIMIT)
                .build();
    }

    static Object accountLock(long accountId) {
        return ACCOUNT_LOCKS.computeIfAbsent(accountId, ignored -> new Object());
    }

    private static int findDailyCounterValue(PetDailyCounterMapper mapper, long accountId,
                                             String date, String counter) {
        Integer value = mapper.findValue(accountId, date, counter);
        return value == null ? 0 : Math.max(0, value);
    }

    private static PetDailyCompanionStatusDTO buildDailyCompanionStatus(PetDailyCounterMapper mapper,
                                                                        long accountId,
                                                                        String today,
                                                                        List<PetDogRecord> dogs) {
        Map<String, PetDailyCompanionDogStatusDTO> dogStatuses = new LinkedHashMap<>();
        for (PetDogRecord dog : dogs) {
            boolean greetCompleted = findDailyCounterValue(mapper, accountId, today,
                    DAILY_COUNTER_GREET_BOND_PREFIX + dog.getId()) > 0;
            boolean feedCompleted = findDailyCounterValue(mapper, accountId, today,
                    DAILY_COUNTER_FEED_BOND_PREFIX + dog.getId()) > 0;
            boolean playCompleted = findDailyCounterValue(mapper, accountId, today,
                    DAILY_COUNTER_GAME_BOND_PREFIX + dog.getId()) > 0;
            boolean outingCompleted = findDailyCounterValue(mapper, accountId, today,
                    DAILY_COUNTER_OUTING_BOND_PREFIX + dog.getId()) > 0;
            int completedCount = 0;
            if (greetCompleted) completedCount++;
            if (feedCompleted) completedCount++;
            if (playCompleted) completedCount++;
            if (outingCompleted) completedCount++;
            dogStatuses.put(dog.getId(), new PetDailyCompanionDogStatusDTO(
                    greetCompleted, feedCompleted, playCompleted, outingCompleted, completedCount, 4));
        }
        return new PetDailyCompanionStatusDTO(dogStatuses);
    }

    private static void grantOutingBondIfAvailable(PetDailyCounterMapper counterMapper,
                                                   PetDogMapper dogMapper,
                                                   long accountId,
                                                   String today,
                                                   PetDogRecord dog,
                                                   long now) {
        if (dog == null || StrUtil.isBlank(dog.getId())) {
            return;
        }
        int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                DAILY_COUNTER_OUTING_BOND_PREFIX, now);
        if (bond != dog.getBond()) {
            dogMapper.updateCareStats(dog.getId(), accountId, bond, now);
        }
    }

    static int applyDailyGreetingBond(SqlSession session,
                                      long accountId,
                                      String today,
                                      PetDogRecord dog,
                                      long now) {
        if (dog == null || StrUtil.isBlank(dog.getId())) {
            return 0;
        }
        PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
        int bond = grantDailyDogBond(counterMapper, accountId, today, dog,
                DAILY_COUNTER_GREET_BOND_PREFIX, now);
        if (bond == dog.getBond()) {
            return 0;
        }
        session.getMapper(PetDogMapper.class).updateCareStats(dog.getId(), accountId, bond, now);
        return 1;
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

    private static int grantDailyDogBondAmount(PetDailyCounterMapper counterMapper,
                                               long accountId,
                                               String today,
                                               PetDogRecord dog,
                                               String sourceCounterPrefix,
                                               int amount,
                                               long now) {
        String totalCounter = DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX + dog.getId();
        int currentBond = dog.getBond();
        int granted = 0;
        for (int i = 0; i < amount; i++) {
            if (findDailyCounterValue(counterMapper, accountId, today, totalCounter) >= DAILY_DOG_BOND_LIMIT) {
                break;
            }
            if (counterMapper.incrementIfUnderLimit(accountId, today,
                    totalCounter, DAILY_DOG_BOND_LIMIT, now) <= 0) {
                break;
            }
            granted++;
        }
        if (granted > 0) {
            counterMapper.incrementByIfUnderLimit(accountId, today,
                    sourceCounterPrefix + dog.getId(), granted, DAILY_DOG_BOND_LIMIT, now);
        }
        return clampDogStat(currentBond + granted);
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

    private static PetDogDTO toDTO(PetDogRecord row) {
        return new PetDogDTO(row.getId(), row.getName(), row.getBreed(), row.getStage(),
                clampDogStat(row.getBond()),
                row.getStatus(), row.getExploreLocation(), row.getExploreEndsAt(),
                row.getExploreSkillId(), row.getExploreSkillSnapshotId(), row.getExploreSkillSnapshotLevel(),
                row.getExploreSkillSnapshotVersion(),
                row.getRaceCount(), row.getRaceFirstCount(), row.getWeeklyPoints());
    }

    private static boolean updateDogGrowthStages(PetDogMapper dogMapper, PetDailyCounterMapper counterMapper,
                                                 long accountId,
                                                 List<PetDogRecord> dogs, long now) {
        boolean changed = false;
        int gameWinCredits = growthGameWinCredits(counterMapper, accountId);
        for (PetDogRecord dog : dogs) {
            changed = updateDogGrowthStage(dogMapper, accountId, dog, gameWinCredits, now) || changed;
        }
        return changed;
    }

    private static boolean updateDogGrowthStage(PetDogMapper dogMapper, long accountId,
                                                PetDogRecord dog, int gameWinCredits, long now) {
        String nextStage = resolveDogGrowthStage(dog, gameWinCredits);
        if (nextStage.equals(dog.getStage())) {
            return false;
        }
        dogMapper.updateStage(dog.getId(), accountId, nextStage, now);
        dog.setStage(nextStage);
        return true;
    }

    private static boolean updateDogGrowthStage(PetDogMapper dogMapper, PetDailyCounterMapper counterMapper,
                                                long accountId,
                                                PetDogRecord dog, long now) {
        return updateDogGrowthStage(dogMapper, accountId, dog, growthGameWinCredits(counterMapper, accountId), now);
    }

    private static String resolveDogGrowthStage(PetDogRecord dog, int gameWinCredits) {
        String currentStage = StrUtil.blankToDefault(dog.getStage(), DOG_STAGE_PUPPY);
        if (DOG_STAGE_CHAMPION.equals(currentStage)
                || (clampDogStat(dog.getBond()) >= DOG_CHAMPION_BOND_THRESHOLD
                && gameWinCredits >= DOG_CHAMPION_GAME_WIN_THRESHOLD)) {
            return DOG_STAGE_CHAMPION;
        }
        if (DOG_STAGE_ADULT.equals(currentStage)
                || (clampDogStat(dog.getBond()) >= DOG_ADULT_BOND_THRESHOLD
                && gameWinCredits >= DOG_ADULT_GAME_WIN_THRESHOLD)) {
            return DOG_STAGE_ADULT;
        }
        return currentStage;
    }

    private static int growthGameWinCredits(PetDailyCounterMapper mapper, long accountId) {
        int gameWins = 0;
        for (Game game : Game.values()) {
            if (game == Game.TACIT_QUIZ) {
                continue;
            }
            gameWins += findLifetimeCounterValue(mapper, accountId, miniGameWinCounter(game));
        }
        int tacitAnswerCredits = findTacitQuizSameAnswers(mapper, accountId)
                / TACIT_QUIZ_SAME_ANSWERS_PER_GROWTH_WIN;
        return gameWins + tacitAnswerCredits;
    }

    private static int clampDogStat(int value) {
        return Math.max(DOG_STAT_MIN, Math.min(DOG_STAT_MAX, value));
    }

    private static PetAssetsDTO toDTO(PetAssetsRecord row) {
        return toDTO(row, row.getEnergyLimit());
    }

    private static PetAssetsDTO toDTO(PetAssetsRecord row, int energyLimit) {
        return new PetAssetsDTO(row.getBones(), row.getFood(), row.getMakeupCards(), row.getDogSlots(),
                Math.min(row.getEnergy(), energyLimit), row.getEnergyDate(), energyLimit);
    }

    private static PetInventoryItemDTO toDTO(PetItemRecord row) {
        return new PetInventoryItemDTO(row.getItemId(), row.getCount());
    }

    private static PetExploreChestDTO toDTO(PetExploreChestRecord row) {
        return new PetExploreChestDTO(row.getId(), row.getChestItemId(), row.getLocation(),
                row.getSourceDogId(), row.getSourceDogName(), row.getSourceDogBreed(),
                row.getDurationHours(), row.getSkillSnapshotId(), row.getSkillSnapshotLevel(),
                row.getSkillSnapshotDefinitionVersion(), row.getCreatedAt());
    }

    private static PetCollectionItemDTO toDTO(PetCollectionRecord row) {
        return new PetCollectionItemDTO(row.getItemId(), row.getCount(), row.isDiscovered());
    }

    private static List<String> resolveActiveDogIds(String savedDogIds, List<PetDogDTO> dogs) {
        if (dogs.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> activeDogIds = new ArrayList<>();
        for (String savedDogId : parseActiveDogIds(savedDogIds)) {
            for (PetDogDTO dog : dogs) {
                if (savedDogId.equals(dog.getId()) && !activeDogIds.contains(savedDogId)) {
                    activeDogIds.add(savedDogId);
                    break;
                }
            }
            if (!activeDogIds.isEmpty()) {
                break;
            }
        }
        if (activeDogIds.isEmpty()) {
            activeDogIds.add(dogs.get(0).getId());
        }
        return activeDogIds;
    }

    private static List<String> updateActiveDogIds(List<String> currentDogIds,
                                                   String dogId,
                                                   boolean active) {
        List<String> nextDogIds = new ArrayList<>(currentDogIds);
        if (active) {
            return Collections.singletonList(dogId);
        }

        if (!nextDogIds.contains(dogId)) {
            return nextDogIds;
        }
        throw new IllegalArgumentException("至少需要保留一只陪伴犬");
    }

    private static List<String> parseActiveDogIds(String savedDogIds) {
        List<String> dogIds = new ArrayList<>();
        if (StrUtil.isBlank(savedDogIds)) {
            return dogIds;
        }
        for (String item : savedDogIds.split(",")) {
            String dogId = StrUtil.trim(item);
            if (StrUtil.isNotBlank(dogId) && !dogIds.contains(dogId)) {
                dogIds.add(dogId);
            }
        }
        return dogIds;
    }

    private static String serializeActiveDogIds(List<String> dogIds) {
        return String.join(",", dogIds);
    }

    private static String primaryActiveDogId(String savedDogIds) {
        List<String> dogIds = parseActiveDogIds(savedDogIds);
        return dogIds.isEmpty() ? null : dogIds.get(0);
    }

    private static final class BreedConfig {
        private static final int DEFAULT_BOND = 10;

        private final boolean hidden;

        private BreedConfig(boolean hidden) {
            this.hidden = hidden;
        }

        private static BreedConfig of(String breed) {
            if ("corgi".equals(breed)
                    || "golden".equals(breed)
                    || "border_collie".equals(breed)
                    || "greyhound".equals(breed)
                    || "poodle".equals(breed)) {
                return new BreedConfig(false);
            }
            if ("shiba".equals(breed)) {
                return new BreedConfig(true);
            }
            if ("husky".equals(breed)) {
                return new BreedConfig(true);
            }
            return null;
        }
    }

    private static final class TreasureSlotOption {
        private final String symbol;
        private final String type;
        private final String label;
        private final int probabilityBp;
        private final int boneAmount;
        private final int quantity;

        private TreasureSlotOption(String symbol, String type, String label, int probabilityBp, int boneAmount,
                                   int quantity) {
            this.symbol = symbol;
            this.type = type;
            this.label = label;
            this.probabilityBp = probabilityBp;
            this.boneAmount = boneAmount;
            this.quantity = quantity;
        }

        private static TreasureSlotOption bones(String symbol, String label, int probabilityBp, int amount) {
            return new TreasureSlotOption(symbol, "bones", label, probabilityBp, amount, amount);
        }

        private static TreasureSlotOption prize(String symbol, String type, String label, int probabilityBp, int quantity) {
            return new TreasureSlotOption(symbol, type, label, probabilityBp, 0, quantity);
        }

        private boolean isBones() {
            return "bones".equals(type);
        }
    }

    private static final class Flip7Card {
        private final String type;
        private final String label;
        private final Integer value;
        private final Integer modifier;
        private final String action;

        private Flip7Card(String type, String label, Integer value, Integer modifier, String action) {
            this.type = type;
            this.label = label;
            this.value = value;
            this.modifier = modifier;
            this.action = action;
        }

        private static Flip7Card number(int value) {
            return new Flip7Card("number", String.valueOf(value), value, null, null);
        }

        private static Flip7Card modifier(String label, Integer modifier) {
            return new Flip7Card("modifier", label, null, modifier, null);
        }

        private static Flip7Card action(String label, String action) {
            return new Flip7Card("action", label, null, null, action);
        }

        private boolean isNumber() {
            return "number".equals(type) && value != null;
        }

        private boolean isModifier() {
            return "modifier".equals(type);
        }
    }

    private static final class Flip7GameState {
        private final PetFlip7StateRecord record;
        private final List<String> drawPile;
        private final List<String> discardPile;
        private PetFlip7RoundDTO activeRound;
        private boolean changed;

        private Flip7GameState(PetFlip7StateRecord record,
                               List<String> drawPile,
                               List<String> discardPile,
                               PetFlip7RoundDTO activeRound,
                               boolean changed) {
            this.record = record;
            this.drawPile = drawPile;
            this.discardPile = discardPile;
            this.activeRound = activeRound;
            this.changed = changed;
        }
    }

    private static final class TreasureHuntSettlement {
        private final int boneReward;
        private final List<PetTreasureHuntExtraRewardDTO> extraRewards;
        private final int bonusSpinReward;
        private final List<String> symbols;
        private final List<String> detailLines;

        private TreasureHuntSettlement(int boneReward, List<PetTreasureHuntExtraRewardDTO> extraRewards,
                                       int bonusSpinReward, List<String> symbols, List<String> detailLines) {
            this.boneReward = boneReward;
            this.extraRewards = extraRewards;
            this.bonusSpinReward = bonusSpinReward;
            this.symbols = symbols;
            this.detailLines = detailLines;
        }
    }

}
