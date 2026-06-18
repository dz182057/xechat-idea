package cn.xeblog.server.game.dograce;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.entity.pet.PetResponseDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.PetAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.PetService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DogRaceService {

    private static final int TRACK_LENGTH = 16;
    private static final int DOG_COUNT = 5;
    private static final int[] DOG_DICE_FACES = {1, 1, 2, 2, 3, 3};
    private static final int LEG_BET_COST = 10;
    private static final int FINAL_BET_COST = 20;
    private static final int BONE_TILE_REWARD = 5;
    private static final int SIGNUP_ENERGY_COST = 3;
    private static final int SIGNUP_BONES_COST = 20;
    private static final int[] LEG_BET_ODDS = {5, 3, 2, 2};
    private static final int[] FINAL_BET_REWARDS = {100, 60, 40, 20};
    private static final int[] RANK_REWARD_BONES = {80, 50, 30, 10, 10};
    private static final int[] WEEKLY_POINTS = {10, 6, 3, 1, 0};
    private static final long HURRY_ROLL_COOLDOWN_MS = 5000L;
    private static final long AUTO_ROLL_DELAY_MS = 25000L;
    private static final double DOG_SKILL_BASE_RATE = 0.05D;
    private static final double DOG_SKILL_RATE_PER_STAT = 0.0025D;
    private static final double DOG_SKILL_MAX_RATE = 0.3D;
    private static final String[] DOG_BREEDS = {"corgi", "golden", "border_collie", "greyhound", "poodle"};
    private static final String[] DOG_NAMES = {"赤豆", "橘子", "青团", "蓝莓", "葡萄"};
    private static final Map<String, RaceState> ROOM_STATES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService AUTO_ROLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dog-race-auto-roll");
        thread.setDaemon(true);
        return thread;
    });

    private DogRaceService() {
    }

    public static void startRace(GameRoom room) {
        DogRaceDTO snapshot = startRaceForTest(room, System.nanoTime(), true);
        broadcast(room, snapshot);
    }

    public static DogRaceDTO startRaceForTest(GameRoom room, long seed) {
        return startRaceForTest(room, seed, false);
    }

    public static DogRaceDTO startRaceForTest(GameRoom room, long seed, boolean chargeSignupCost) {
        RaceState state = createState(room, seed, chargeSignupCost);
        ROOM_STATES.put(room.getId(), state);
        DogRaceDTO snapshot = snapshot(room, state, DogRaceDTO.Event.RACE_INIT);
        snapshot.getBroadcasts().add("🏁 狗狗赛跑开赛，玩家可以下注、放地块或催骰。");
        snapshot.getBroadcasts().addAll(state.initialBroadcasts);
        scheduleAutoRoll(room.getId(), state);
        return snapshot;
    }

    public static DogRaceDTO createInitialSnapshot(GameRoom room, long seed) {
        RaceState state = createState(room, seed, false);
        return snapshot(room, state, DogRaceDTO.Event.RACE_INIT);
    }

    public static List<DogRaceDTO> simulateRace(GameRoom room, long seed) {
        RaceState state = createState(room, seed, false);
        List<DogRaceDTO> events = new ArrayList<>();
        events.add(snapshot(room, state, DogRaceDTO.Event.RACE_INIT));
        int guard = 0;

        while (!hasFinished(state) && guard < 500) {
            guard++;
            DogRaceDTO event = rollNext(room, state);
            events.add(event);
            if (event.getEvent() == DogRaceDTO.Event.RACE_SETTLE) {
                break;
            }
        }
        return events;
    }

    public static void handle(User user, GameRoom room, GameDTO body) {
        DogRaceDTO request = body instanceof DogRaceDTO
                ? (DogRaceDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), DogRaceDTO.class);
        if (request == null || request.getEvent() == null) {
            return;
        }
        DogRaceDTO result = applyRequestForTest(room, user.getIdentityKey(), user.getUsername(), request,
                System.currentTimeMillis(), true);
        broadcast(room, result);
    }

    public static DogRaceDTO applyRequestForTest(GameRoom room, String playerKey, String playerName, DogRaceDTO request) {
        return applyRequestForTest(room, playerKey, playerName, request, 0L, false);
    }

    public static DogRaceDTO applyRequestForTest(GameRoom room, String playerKey, String playerName, DogRaceDTO request,
                                                 long nowMs, boolean enforceHurryCooldown) {
        RaceState state = ROOM_STATES.get(room.getId());
        if (state == null) {
            return error(room, "当前没有进行中的狗狗赛跑");
        }
        if (state.finished) {
            return error(room, "比赛已经结束");
        }

        switch (request.getEvent()) {
            case ROLL_REQ:
                if (enforceHurryCooldown && state.lastHurryRollAt > 0
                        && nowMs - state.lastHurryRollAt < HURRY_ROLL_COOLDOWN_MS) {
                    return error(room, "催骰冷却中，请稍后再试");
                }
                if (enforceHurryCooldown) {
                    state.lastHurryRollAt = nowMs;
                }
                DogRaceDTO roll = rollNext(room, state);
                scheduleAutoRoll(room.getId(), state);
                return roll;
            case BET_LEG_REQ:
                return placeLegBet(room, state, playerKey, playerName, request.getDogId());
            case BET_FINAL_REQ:
                return placeFinalBet(room, state, playerKey, playerName, request.getDogId(), request.getBetKind());
            case PLACE_TILE_REQ:
                return placeTile(room, state, playerKey, playerName, request.getCell(), request.getTileType());
            default:
                return error(room, "暂不支持的狗狗赛跑操作");
        }
    }

    public static void clearRoom(String roomId) {
        RaceState state = ROOM_STATES.remove(roomId);
        if (state != null) {
            state.autoRollScheduled = false;
            state.autoRollVersion++;
        }
    }

    public static boolean hasAutoRollScheduledForTest(String roomId) {
        RaceState state = ROOM_STATES.get(roomId);
        return state != null && state.autoRollScheduled;
    }

    private static RaceState createState(GameRoom room, long seed, boolean chargeSignupCost) {
        Random random = new Random(seed);
        RaceState state = new RaceState(random);
        initDogs(room, state, random, chargeSignupCost);
        initCats(state, random);
        state.diceBag = createDiceBag(state);
        return state;
    }

    private static DogRaceDTO placeLegBet(GameRoom room, RaceState state, String playerKey, String playerName, String dogId) {
        if (!state.units.containsKey(dogId) || !"dog".equals(state.units.get(dogId).type)) {
            return error(room, "请选择有效的参赛狗下注");
        }
        String betKey = playerKey + ":" + state.legNo + ":" + dogId;
        if (state.legBetKeys.contains(betKey)) {
            return error(room, "本赛段已经押过这只狗");
        }
        int dogBetCount = 0;
        for (LegBet bet : state.legBets) {
            if (bet.dogId.equals(dogId)) {
                dogBetCount++;
            }
        }
        if (dogBetCount >= LEG_BET_ODDS.length) {
            return error(room, "这只狗本赛段下注牌已经被领完");
        }
        Long accountId;
        PetProfileDTO profile;
        try {
            accountId = resolveBetAccountId(room, playerKey);
            profile = spendBetCost(accountId, LEG_BET_COST);
        } catch (IllegalArgumentException e) {
            return error(room, e.getMessage());
        }
        int odds = LEG_BET_ODDS[dogBetCount];
        state.legBetKeys.add(betKey);
        state.legBets.add(new LegBet(playerKey, playerName, accountId == null ? 0L : accountId, dogId, odds));
        DogRaceDTO dto = snapshot(room, state, DogRaceDTO.Event.BET_LEG);
        dto.setBroadcast(playerName + " 下了 " + state.units.get(dogId).name + " 的赛段注（🦴" + LEG_BET_COST + "），赔率 " + odds + "。");
        dto.getBroadcasts().add(dto.getBroadcast());
        sendPetProfileUpdate(room, playerKey, profile);
        return dto;
    }

    private static DogRaceDTO placeFinalBet(GameRoom room, RaceState state, String playerKey, String playerName, String dogId, String betKind) {
        if (!state.units.containsKey(dogId) || !"dog".equals(state.units.get(dogId).type)) {
            return error(room, "请选择有效的参赛狗暗注");
        }
        String kind = "last".equals(betKind) ? "last" : "champion";
        String betKey = playerKey + ":" + kind;
        if (state.finalBetKeys.contains(betKey)) {
            return error(room, "本场已经下过这个暗注");
        }
        Long accountId;
        PetProfileDTO profile;
        try {
            accountId = resolveBetAccountId(room, playerKey);
            profile = spendBetCost(accountId, FINAL_BET_COST);
        } catch (IllegalArgumentException e) {
            return error(room, e.getMessage());
        }
        state.finalBetKeys.add(betKey);
        state.finalBets.add(new FinalBet(playerKey, playerName, accountId == null ? 0L : accountId, dogId, kind));
        DogRaceDTO dto = snapshot(room, state, DogRaceDTO.Event.BET_FINAL);
        dto.setBroadcast(playerName + " 下了一张" + ("last".equals(kind) ? "垫底" : "冠军") + "暗注（🦴" + FINAL_BET_COST + "）。");
        dto.getBroadcasts().add(dto.getBroadcast());
        sendPetProfileUpdate(room, playerKey, profile);
        return dto;
    }

    private static DogRaceDTO placeTile(GameRoom room, RaceState state, String playerKey, String playerName, int cell, String tileType) {
        if (state.tilePlayerKeys.contains(playerKey)) {
            return error(room, "本赛段已经放过地块");
        }
        if (cell < 2 || cell > 15) {
            return error(room, "地块只能放在 2 到 15 格");
        }
        if (!"bone".equals(tileType) && !"mud".equals(tileType)) {
            return error(room, "请选择有效的地块类型");
        }
        if (hasUnitAt(state, cell)) {
            return error(room, "有狗狗或野猫的格子不能放地块");
        }
        for (DogRaceTile tile : state.tiles) {
            if (Math.abs(tile.cell - cell) <= 1) {
                return error(room, "地块不能相邻");
            }
        }

        Long accountId;
        try {
            accountId = resolveRoomAccountId(room, playerKey, "请先登录账号再放地块");
        } catch (IllegalArgumentException e) {
            return error(room, e.getMessage());
        }
        state.tilePlayerKeys.add(playerKey);
        state.tiles.add(new DogRaceTile(cell, tileType, playerKey, playerName, accountId == null ? 0L : accountId));
        DogRaceDTO dto = snapshot(room, state, DogRaceDTO.Event.PLACE_TILE);
        dto.setBroadcast(playerName + " 在第 " + cell + " 格放了" + ("bone".equals(tileType) ? "骨头" : "泥坑") + "。");
        dto.getBroadcasts().add(dto.getBroadcast());
        return dto;
    }

    private static DogRaceDTO rollNext(GameRoom room, RaceState state) {
        if (state.diceBag.isEmpty()) {
            return settleLeg(room, state);
        }

        String die = state.diceBag.remove(state.random.nextInt(state.diceBag.size()));
        DogRaceDTO roll = new DogRaceDTO(room.getId());
        roll.setEvent(DogRaceDTO.Event.ROLL);
        roll.setMode(mode(room));
        roll.setPhase("running");
        roll.setLegNo(state.legNo);
        if ("cat".equals(die)) {
            moveCat(room, state, state.random, roll);
        } else {
            moveDog(room, state, die, state.random, roll);
        }
        fillSnapshotFields(roll, state);

        if (hasFinished(state)) {
            state.finished = true;
            DogRaceDTO settle = snapshot(room, state, DogRaceDTO.Event.RACE_SETTLE);
            settle.setPhase("raceSettle");
            settle.setRankings(rankings(state));
            settle.getBroadcasts().add(roll.getBroadcast());
            settle.getBroadcasts().addAll(settleFinalBets(room, state, settle.getRankings()));
            settle.getBroadcasts().addAll(applyOwnedDogRaceResults(room, state, settle.getRankings()));
            if (!settle.getRankings().isEmpty()) {
                RaceUnit winner = state.units.get(settle.getRankings().get(0).getDogId());
                settle.getBroadcasts().add("🏁 " + (winner == null ? "狗狗" : winner.name) + " 率先冲过终点，比赛结束！");
            }
            return settle;
        }
        if (state.diceBag.isEmpty()) {
            DogRaceDTO settle = settleLeg(room, state);
            settle.getBroadcasts().add(0, roll.getBroadcast());
            return settle;
        }
        return roll;
    }

    private static DogRaceDTO settleLeg(GameRoom room, RaceState state) {
        DogRaceDTO settle = snapshot(room, state, DogRaceDTO.Event.LEG_SETTLE);
        settle.setPhase("legSettle");
        settle.setRankings(rankings(state));
        settle.getBroadcasts().add("第 " + state.legNo + " 赛段结算，地块和赛段下注牌已清空。");
        settle.getBroadcasts().addAll(settleLegBets(room, state, settle.getRankings()));
        state.legNo++;
        state.diceBag = createDiceBag(state);
        state.tiles.clear();
        state.legBetKeys.clear();
        state.legBets.clear();
        state.tilePlayerKeys.clear();
        settle.setLegNo(state.legNo);
        settle.setTiles(new ArrayList<DogRaceDTO.Tile>());
        settle.getBroadcasts().add("第 " + state.legNo + " 赛段开始。");
        return settle;
    }

    private static void initDogs(GameRoom room, RaceState state, Random random, boolean chargeSignupCost) {
        int slot = 1;
        if ("owned_dog".equals(mode(room))) {
            for (GameRoom.Player player : room.getUsers().values()) {
                if (slot > DOG_COUNT || player.getAccountId() <= 0L) {
                    continue;
                }
                PetProfileDTO.Dog petDog = PetService.findRaceDog(player.getAccountId());
                if (petDog == null) {
                    continue;
                }
                if (chargeSignupCost) {
                    try {
                        PetProfileDTO profile = PetService.spendRaceSignup(
                                player.getAccountId(),
                                petDog.getId(),
                                SIGNUP_ENERGY_COST,
                                SIGNUP_BONES_COST);
                        sendPetProfileUpdate(room, player.getId(), profile);
                        state.initialBroadcasts.add(player.getUsername() + " 带 " + petDog.getName()
                                + " 报名参赛，消耗 🦴" + SIGNUP_BONES_COST + " 和活力 " + SIGNUP_ENERGY_COST + "。");
                    } catch (IllegalArgumentException e) {
                        state.initialBroadcasts.add(player.getUsername() + " 的狗狗报名失败：" + e.getMessage() + "。");
                        continue;
                    }
                }
                RaceUnit unit = new RaceUnit(petDog.getId(), petDog.getName(), "dog", slot, rollDog(random));
                unit.breed = petDog.getBreed();
                unit.ownerPlayerKey = player.getId();
                unit.ownerName = player.getUsername();
                unit.ownerAccountId = player.getAccountId();
                unit.skillStatValue = skillStatValue(petDog);
                state.units.put(unit.id, unit);
                push(state.stacks, unit.position, unit.id);
                slot++;
            }
        }
        while (slot <= DOG_COUNT) {
            int index = slot - 1;
            String dogId = "dog-" + slot;
            while (state.units.containsKey(dogId)) {
                dogId = "dog-bot-" + slot;
            }
            RaceUnit unit = new RaceUnit(dogId, DOG_NAMES[index], "dog", slot, rollDog(random));
            unit.breed = DOG_BREEDS[index];
            state.units.put(dogId, unit);
            push(state.stacks, unit.position, dogId);
            slot++;
        }
    }

    private static void initCats(RaceState state, Random random) {
        RaceUnit black = new RaceUnit("black_cat", "黑猫", "cat", 0, TRACK_LENGTH + 1 - rollDog(random));
        RaceUnit white = new RaceUnit("white_cat", "白猫", "cat", 0, TRACK_LENGTH + 1 - rollDog(random));
        state.units.put(black.id, black);
        state.units.put(white.id, white);
        push(state.stacks, black.position, black.id);
        push(state.stacks, white.position, white.id);
    }

    private static void moveDog(GameRoom room, RaceState state, String dogId, Random random, DogRaceDTO roll) {
        int steps = rollDog(random);
        MoveResult result = moveUnit(room, state, dogId, steps, true);
        RaceUnit dog = state.units.get(dogId);
        String skillName = tryApplyDogSkill(state, dog);
        if (skillName != null) {
            roll.setSkillName(skillName);
        }
        int to = dog == null ? result.to : dog.position;
        roll.setDie(new DogRaceDTO.Die("dog", dog == null ? 0 : dog.slot, dogId, null, steps));
        roll.setBroadcast("🎲 " + (dog == null ? dogId : dog.name) + " 掷出 " + steps + " 点，冲到第 " + to + " 格！"
                + (skillName == null ? "" : " ✨触发【" + skillName + "】")
                + (result.tileBroadcast == null ? "" : " " + result.tileBroadcast));
    }

    private static String tryApplyDogSkill(RaceState state, RaceUnit dog) {
        if (dog == null || dog.ownerAccountId <= 0L || dog.breed == null || state.usedSkillDogIds.contains(dog.id)) {
            return null;
        }
        DogRaceSkill skill = skillForBreed(dog.breed);
        if (skill == null) {
            return null;
        }
        double rate = Math.min(DOG_SKILL_BASE_RATE + Math.max(0, Math.min(100, dog.skillStatValue)) * DOG_SKILL_RATE_PER_STAT,
                DOG_SKILL_MAX_RATE);
        if (state.random.nextDouble() >= rate) {
            return null;
        }
        state.usedSkillDogIds.add(dog.id);
        dog.skillName = skill.name;
        dog.skillTriggered = true;
        if (skill.extraSteps > 0) {
            moveUnit(null, state, dog.id, skill.extraSteps, false);
        }
        return skill.name;
    }

    private static DogRaceSkill skillForBreed(String breed) {
        switch (breed) {
            case "corgi":
                return new DogRaceSkill("人来疯", 1);
            case "golden":
                return new DogRaceSkill("捡球高手", 0);
            case "border_collie":
                return new DogRaceSkill("聪明走位", 0);
            case "greyhound":
                return new DogRaceSkill("猎影冲刺", 1);
            case "poodle":
                return new DogRaceSkill("轻盈跳跃", 1);
            case "native":
                return new DogRaceSkill("主场作战", 1);
            case "shiba":
                return new DogRaceSkill("梗王翻盘", 2);
            case "husky":
                return new DogRaceSkill("拆家", 1);
            default:
                return null;
        }
    }

    private static int skillStatValue(PetProfileDTO.Dog dog) {
        if (dog == null || dog.getBreed() == null) {
            return 0;
        }
        switch (dog.getBreed()) {
            case "corgi":
            case "shiba":
                return dog.getBurst();
            case "golden":
                return dog.getBond();
            case "border_collie":
                return dog.getWisdom();
            case "greyhound":
            case "poodle":
            case "husky":
                return dog.getSpeed();
            case "native":
                return dog.getStamina();
            default:
                return 0;
        }
    }

    private static void moveCat(GameRoom room, RaceState state, Random random, DogRaceDTO roll) {
        String catId = random.nextBoolean() ? "black_cat" : "white_cat";
        int steps = rollDog(random);
        MoveResult result = moveUnit(room, state, catId, -steps, true);
        RaceUnit cat = state.units.get(catId);
        roll.setDie(new DogRaceDTO.Die("cat", 0, null, catId, steps));
        roll.setBroadcast("🐈 " + (cat == null ? "野猫" : cat.name) + " 逆行 " + steps + " 格，添乱到第 " + result.to + " 格！"
                + (result.tileBroadcast == null ? "" : " " + result.tileBroadcast));
    }

    private static MoveResult moveUnit(GameRoom room, RaceState state, String unitId, int delta, boolean checkTile) {
        MoveResult result = moveStack(state, unitId, delta, delta < 0);
        if (checkTile) {
            DogRaceTile tile = findTile(state, result.to);
            if (tile != null) {
                if ("bone".equals(tile.tileType)) {
                    String tileBroadcast = applyBoneTileReward(room, state.units.get(unitId), tile);
                    result = moveStack(state, unitId, 1, false);
                    result.tileBroadcast = tileBroadcast;
                } else if ("mud".equals(tile.tileType)) {
                    result = moveStack(state, unitId, -1, true);
                }
            }
        }
        return result;
    }

    private static String applyBoneTileReward(GameRoom room, RaceUnit unit, DogRaceTile tile) {
        if (tile.ownerAccountId > 0L) {
            PetProfileDTO profile = PetService.changeBones(tile.ownerAccountId, BONE_TILE_REWARD);
            sendPetProfileUpdate(room, tile.ownerPlayerKey, profile);
        }
        return "🦴 " + (unit == null ? "移动单位" : unit.name) + " 踩到 "
                + tile.ownerName + " 放的骨头，" + tile.ownerName + " 获得 🦴" + BONE_TILE_REWARD + "。";
    }

    private static MoveResult moveStack(RaceState state, String unitId, int delta, boolean toBottom) {
        RaceUnit unit = state.units.get(unitId);
        if (unit == null) {
            return new MoveResult(0, 0);
        }
        int from = unit.position;
        List<String> stack = state.stacks.get(from);
        int index = stack == null ? -1 : stack.indexOf(unitId);
        List<String> carried = new ArrayList<>();
        if (stack != null && index >= 0) {
            carried.addAll(stack.subList(index, stack.size()));
            stack.subList(index, stack.size()).clear();
            if (stack.isEmpty()) {
                state.stacks.remove(from);
            }
        } else {
            carried.add(unitId);
        }
        int to = delta < 0 ? Math.max(1, from + delta) : from + delta;
        List<String> target = state.stacks.computeIfAbsent(to, key -> new ArrayList<String>());
        if (toBottom) {
            target.addAll(0, carried);
        } else {
            target.addAll(carried);
        }
        for (String carriedId : carried) {
            RaceUnit carriedUnit = state.units.get(carriedId);
            if (carriedUnit != null) {
                carriedUnit.position = to;
            }
        }
        return new MoveResult(from, to);
    }

    private static DogRaceDTO snapshot(GameRoom room, RaceState state, DogRaceDTO.Event event) {
        DogRaceDTO dto = new DogRaceDTO(room.getId());
        dto.setEvent(event);
        dto.setMode(mode(room));
        dto.setPhase(event == DogRaceDTO.Event.RACE_SETTLE ? "raceSettle" : "running");
        dto.setLegNo(state.legNo);
        dto.setBroadcasts(new ArrayList<String>());
        fillSnapshotFields(dto, state);
        return dto;
    }

    private static void fillSnapshotFields(DogRaceDTO dto, RaceState state) {
        List<DogRaceDTO.Participant> participants = new ArrayList<>();
        List<DogRaceDTO.Cat> cats = new ArrayList<>();
        for (RaceUnit unit : state.units.values()) {
            int stackIndex = stackIndex(state, unit);
            if ("dog".equals(unit.type)) {
                participants.add(new DogRaceDTO.Participant(
                        unit.slot,
                        unit.id,
                        unit.name,
                        unit.breed,
                        unit.ownerPlayerKey,
                        unit.ownerName,
                        unit.position,
                        stackIndex,
                        null,
                        unit.skillName,
                        unit.skillTriggered
                ));
            } else {
                cats.add(new DogRaceDTO.Cat(unit.id, unit.name, unit.position, stackIndex));
            }
        }
        participants.sort(Comparator.comparingInt(DogRaceDTO.Participant::getSlot));
        dto.setParticipants(participants);
        dto.setCats(cats);
        dto.setTiles(toTileDTOs(state.tiles));
    }

    private static List<DogRaceDTO.Tile> toTileDTOs(List<DogRaceTile> tiles) {
        List<DogRaceDTO.Tile> result = new ArrayList<>();
        for (DogRaceTile tile : tiles) {
            result.add(new DogRaceDTO.Tile(tile.cell, tile.tileType, tile.ownerPlayerKey, tile.ownerName));
        }
        return result;
    }

    private static List<DogRaceDTO.Ranking> rankings(RaceState state) {
        List<RaceUnit> dogs = new ArrayList<>();
        for (RaceUnit unit : state.units.values()) {
            if ("dog".equals(unit.type)) {
                dogs.add(unit);
            }
        }
        dogs.sort((left, right) -> {
            if (left.position != right.position) {
                return Integer.compare(right.position, left.position);
            }
            return Integer.compare(stackIndex(state, right), stackIndex(state, left));
        });
        List<DogRaceDTO.Ranking> rankings = new ArrayList<>();
        for (int i = 0; i < dogs.size(); i++) {
            RaceUnit dog = dogs.get(i);
            rankings.add(new DogRaceDTO.Ranking(
                    dog.id,
                    dog.slot,
                    i + 1,
                    dog.ownerPlayerKey,
                    i < RANK_REWARD_BONES.length ? RANK_REWARD_BONES[i] : 0,
                    i < WEEKLY_POINTS.length ? WEEKLY_POINTS[i] : 0));
        }
        return rankings;
    }

    private static List<String> applyOwnedDogRaceResults(GameRoom room, RaceState state, List<DogRaceDTO.Ranking> rankings) {
        List<String> broadcasts = new ArrayList<>();
        for (DogRaceDTO.Ranking ranking : rankings) {
            RaceUnit dog = state.units.get(ranking.getDogId());
            if (dog == null || dog.ownerAccountId <= 0L) {
                continue;
            }
            PetRaceResultDTO result = new PetRaceResultDTO();
            result.setDogId(dog.id);
            result.setRank(ranking.getRank());
            try {
                PetProfileDTO profile = PetService.applyRaceResult(dog.ownerAccountId, result);
                int rewardBones = ranking.getRewardBones() == null ? 0 : ranking.getRewardBones();
                if (rewardBones > 0) {
                    profile = PetService.changeBones(dog.ownerAccountId, rewardBones);
                    broadcasts.add("🏆 " + dog.name + " 第 " + ranking.getRank() + " 名，名次奖获得 🦴" + rewardBones + "。");
                }
                sendPetProfileUpdate(room, dog.ownerPlayerKey, profile);
                broadcasts.add("🐾 " + dog.name + " 的赛跑成长记录已更新。");
            } catch (Exception e) {
                broadcasts.add("🐾 " + dog.name + " 的成长记录更新失败：" + e.getMessage());
            }
        }
        return broadcasts;
    }

    private static void sendPetProfileUpdate(GameRoom room, String playerKey, PetProfileDTO profile) {
        if (room == null || playerKey == null || profile == null) {
            return;
        }
        GameRoom.Player player = room.getUsers().get(playerKey);
        if (player == null) {
            return;
        }
        User user = UserCache.get(player.getChannelId());
        if (user == null) {
            return;
        }
        PetRequestDTO request = new PetRequestDTO();
        request.setPetAction(PetAction.RACE_RESULT);
        user.send(ResponseBuilder.build(null, PetResponseDTO.ok(request, profile), MessageType.PET));
    }

    private static List<String> settleLegBets(GameRoom room, RaceState state, List<DogRaceDTO.Ranking> rankings) {
        List<String> broadcasts = new ArrayList<>();
        if (state.legBets.isEmpty() || rankings.isEmpty()) {
            return broadcasts;
        }
        String firstDogId = rankings.get(0).getDogId();
        String secondDogId = rankings.size() > 1 ? rankings.get(1).getDogId() : null;
        for (LegBet bet : state.legBets) {
            if (bet.dogId.equals(firstDogId)) {
                int reward = LEG_BET_COST + bet.odds * LEG_BET_COST;
                grantBetReward(room, bet.playerKey, bet.accountId, reward);
                broadcasts.add(bet.playerName + " 的赛段注押中第一，返还 🦴" + reward + "。");
            } else if (bet.dogId.equals(secondDogId)) {
                grantBetReward(room, bet.playerKey, bet.accountId, LEG_BET_COST);
                broadcasts.add(bet.playerName + " 的赛段注押中第二，返还 🦴" + LEG_BET_COST + "。");
            } else {
                broadcasts.add(bet.playerName + " 的赛段注未命中。");
            }
        }
        return broadcasts;
    }

    private static List<String> settleFinalBets(GameRoom room, RaceState state, List<DogRaceDTO.Ranking> rankings) {
        List<String> broadcasts = new ArrayList<>();
        if (state.finalBets.isEmpty() || rankings.isEmpty()) {
            return broadcasts;
        }
        String championDogId = rankings.get(0).getDogId();
        String lastDogId = rankings.get(rankings.size() - 1).getDogId();
        int championHitIndex = 0;
        int lastHitIndex = 0;
        for (FinalBet bet : state.finalBets) {
            String targetDogId = "last".equals(bet.betKind) ? lastDogId : championDogId;
            if (bet.dogId.equals(targetDogId)) {
                int hitIndex = "last".equals(bet.betKind) ? lastHitIndex++ : championHitIndex++;
                int reward = hitIndex < FINAL_BET_REWARDS.length
                        ? FINAL_BET_REWARDS[hitIndex]
                        : FINAL_BET_REWARDS[FINAL_BET_REWARDS.length - 1];
                grantBetReward(room, bet.playerKey, bet.accountId, reward);
                broadcasts.add(bet.playerName + " 的" + ("last".equals(bet.betKind) ? "垫底" : "冠军") + "暗注命中，获得 🦴" + reward + "。");
            } else {
                broadcasts.add(bet.playerName + " 的" + ("last".equals(bet.betKind) ? "垫底" : "冠军") + "暗注未命中。");
            }
        }
        return broadcasts;
    }

    private static Long resolveBetAccountId(GameRoom room, String playerKey) {
        return resolveRoomAccountId(room, playerKey, "请先登录账号再下注");
    }

    private static Long resolveRoomAccountId(GameRoom room, String playerKey, String errorMessage) {
        if (playerKey == null || room == null || room.getUsers() == null || !room.getUsers().containsKey(playerKey)) {
            return null;
        }
        GameRoom.Player player = room.getUsers().get(playerKey);
        long accountId = player == null ? 0L : player.getAccountId();
        if (accountId <= 0L) {
            throw new IllegalArgumentException(errorMessage);
        }
        return accountId;
    }

    private static PetProfileDTO spendBetCost(Long accountId, int cost) {
        if (accountId == null) {
            return null;
        }
        return PetService.changeBones(accountId, -cost);
    }

    private static void grantBetReward(GameRoom room, String playerKey, long accountId, int reward) {
        if (accountId <= 0L || reward <= 0) {
            return;
        }
        PetProfileDTO profile = PetService.changeBones(accountId, reward);
        sendPetProfileUpdate(room, playerKey, profile);
    }

    private static DogRaceDTO error(GameRoom room, String message) {
        DogRaceDTO dto = new DogRaceDTO(room.getId());
        dto.setEvent(DogRaceDTO.Event.ERROR);
        dto.setMode(mode(room));
        dto.setPhase("running");
        dto.setMessage(message);
        dto.setBroadcast(message);
        dto.setBroadcasts(new ArrayList<String>());
        dto.getBroadcasts().add(message);
        return dto;
    }

    private static boolean hasFinished(RaceState state) {
        for (RaceUnit unit : state.units.values()) {
            if ("dog".equals(unit.type) && unit.position > TRACK_LENGTH) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnitAt(RaceState state, int cell) {
        List<String> stack = state.stacks.get(cell);
        return stack != null && !stack.isEmpty();
    }

    private static DogRaceTile findTile(RaceState state, int cell) {
        for (DogRaceTile tile : state.tiles) {
            if (tile.cell == cell) {
                return tile;
            }
        }
        return null;
    }

    private static int stackIndex(RaceState state, RaceUnit unit) {
        List<String> stack = state.stacks.get(unit.position);
        return stack == null ? -1 : stack.indexOf(unit.id);
    }

    private static List<String> createDiceBag(RaceState state) {
        List<String> diceBag = new ArrayList<>();
        for (RaceUnit unit : state.units.values()) {
            if ("dog".equals(unit.type)) {
                diceBag.add(unit.id);
            }
        }
        diceBag.add("cat");
        return diceBag;
    }

    private static int rollDog(Random random) {
        return DOG_DICE_FACES[random.nextInt(DOG_DICE_FACES.length)];
    }

    private static void push(Map<Integer, List<String>> stacks, int position, String unitId) {
        stacks.computeIfAbsent(position, key -> new ArrayList<String>()).add(unitId);
    }

    private static String mode(GameRoom room) {
        return room.getDogRaceMode() == null || room.getDogRaceMode().trim().isEmpty()
                ? "pure_betting"
                : room.getDogRaceMode();
    }

    private static void broadcast(GameRoom room, DogRaceDTO dto) {
        room.getUsers().forEach((key, player) -> {
            User user = UserCache.get(player.getChannelId());
            if (user != null) {
                user.send(ResponseBuilder.build(null, dto, MessageType.GAME));
            }
        });
    }

    private static void scheduleAutoRoll(String roomId, RaceState state) {
        if (state.finished) {
            state.autoRollScheduled = false;
            return;
        }
        long version = ++state.autoRollVersion;
        state.autoRollScheduled = true;
        AUTO_ROLL_EXECUTOR.schedule(() -> autoRoll(roomId, version), AUTO_ROLL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void autoRoll(String roomId, long version) {
        RaceState state = ROOM_STATES.get(roomId);
        if (state == null || state.finished || state.autoRollVersion != version) {
            return;
        }
        GameRoom room = GameRoomCache.getGameRoom(roomId);
        if (room == null) {
            clearRoom(roomId);
            return;
        }
        DogRaceDTO event = rollNext(room, state);
        broadcast(room, event);
        scheduleAutoRoll(roomId, state);
    }

    private static class RaceState {
        private final Map<String, RaceUnit> units = new LinkedHashMap<>();
        private final Map<Integer, List<String>> stacks = new LinkedHashMap<>();
        private final Random random;
        private final List<DogRaceTile> tiles = new ArrayList<>();
        private final List<LegBet> legBets = new ArrayList<>();
        private final List<FinalBet> finalBets = new ArrayList<>();
        private final Set<String> legBetKeys = new HashSet<>();
        private final Set<String> finalBetKeys = new HashSet<>();
        private final Set<String> tilePlayerKeys = new HashSet<>();
        private final Set<String> usedSkillDogIds = new HashSet<>();
        private final List<String> initialBroadcasts = new ArrayList<>();
        private int legNo = 1;
        private List<String> diceBag = new ArrayList<>();
        private boolean finished;
        private long lastHurryRollAt;
        private long autoRollVersion;
        private boolean autoRollScheduled;

        private RaceState(Random random) {
            this.random = random;
        }
    }

    private static class RaceUnit {
        private final String id;
        private final String name;
        private final String type;
        private final int slot;
        private int position;
        private String breed;
        private String ownerPlayerKey;
        private String ownerName;
        private long ownerAccountId;
        private int skillStatValue;
        private String skillName;
        private boolean skillTriggered;

        private RaceUnit(String id, String name, String type, int slot, int position) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.slot = slot;
            this.position = position;
        }
    }

    private static class DogRaceSkill {
        private final String name;
        private final int extraSteps;

        private DogRaceSkill(String name, int extraSteps) {
            this.name = name;
            this.extraSteps = extraSteps;
        }
    }

    private static class LegBet {
        private final String playerKey;
        private final String playerName;
        private final long accountId;
        private final String dogId;
        private final int odds;

        private LegBet(String playerKey, String playerName, long accountId, String dogId, int odds) {
            this.playerKey = playerKey;
            this.playerName = playerName;
            this.accountId = accountId;
            this.dogId = dogId;
            this.odds = odds;
        }
    }

    private static class FinalBet {
        private final String playerKey;
        private final String playerName;
        private final long accountId;
        private final String dogId;
        private final String betKind;

        private FinalBet(String playerKey, String playerName, long accountId, String dogId, String betKind) {
            this.playerKey = playerKey;
            this.playerName = playerName;
            this.accountId = accountId;
            this.dogId = dogId;
            this.betKind = betKind;
        }
    }

    private static class DogRaceTile {
        private final int cell;
        private final String tileType;
        private final String ownerPlayerKey;
        private final String ownerName;
        private final long ownerAccountId;

        private DogRaceTile(int cell, String tileType, String ownerPlayerKey, String ownerName, long ownerAccountId) {
            this.cell = cell;
            this.tileType = tileType;
            this.ownerPlayerKey = ownerPlayerKey;
            this.ownerName = ownerName;
            this.ownerAccountId = ownerAccountId;
        }
    }

    private static class MoveResult {
        private final int from;
        private final int to;
        private String tileBroadcast;

        private MoveResult(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }
}
