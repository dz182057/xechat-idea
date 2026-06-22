package cn.xeblog.server.game.dograce;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.pet.PetService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DogRaceServiceTest {

    @Test
    public void createInitialSnapshotShouldUseAuthoritativeRaceRules() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-test");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        DogRaceDTO snapshot = DogRaceService.createInitialSnapshot(room, 20260617L);

        Assert.assertEquals(Game.DOG_RACE, snapshot.getGame());
        Assert.assertEquals(DogRaceDTO.Event.RACE_INIT, snapshot.getEvent());
        Assert.assertEquals("pure_betting", snapshot.getMode());
        Assert.assertEquals(5, snapshot.getParticipants().size());
        Assert.assertEquals(2, snapshot.getCats().size());
        Assert.assertEquals(1, snapshot.getLegNo());
        Assert.assertTrue(snapshot.getParticipants().stream().allMatch(dog -> dog.getPosition() >= 1 && dog.getPosition() <= 3));
        Assert.assertTrue(snapshot.getCats().stream().allMatch(cat -> cat.getPosition() >= 14 && cat.getPosition() <= 16));
    }

    @Test
    public void simulateRaceShouldProduceUniqueFinalRanks() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-test");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        List<DogRaceDTO> events = DogRaceService.simulateRace(room, 20260617L);
        DogRaceDTO settle = events.get(events.size() - 1);
        Set<Integer> ranks = new HashSet<>();

        Assert.assertEquals(DogRaceDTO.Event.RACE_SETTLE, settle.getEvent());
        Assert.assertEquals(5, settle.getRankings().size());
        settle.getRankings().forEach(ranking -> ranks.add(ranking.getRank()));
        Assert.assertEquals(5, ranks.size());
        for (int rank = 1; rank <= 5; rank++) {
            Assert.assertTrue(ranks.contains(rank));
        }
    }

    @Test
    public void roomStateShouldAcceptBetsTilesAndRollRequests() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-room-state");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        DogRaceDTO snapshot = DogRaceService.startRaceForTest(room, 20260617L);
        String dogId = snapshot.getParticipants().get(0).getDogId();
        Assert.assertEquals(5, snapshot.getLegBetPools().size());
        Assert.assertEquals(0, snapshot.getLegBetPools().get(0).getBetCount());
        Assert.assertEquals(Integer.valueOf(5), snapshot.getLegBetPools().get(0).getNextOdds());

        DogRaceDTO legBet = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_LEG_REQ, dogId, null, 0, null));
        Assert.assertEquals(DogRaceDTO.Event.BET_LEG, legBet.getEvent());
        Assert.assertTrue(legBet.getBroadcast().contains("赛段注"));
        Assert.assertEquals(1, legBet.getLegBetPools().stream()
                .filter(pool -> dogId.equals(pool.getDogId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应下发赛段下注池"))
                .getBetCount());

        DogRaceDTO finalBet = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_FINAL_REQ, dogId, "champion", 0, null));
        Assert.assertEquals(DogRaceDTO.Event.BET_FINAL, finalBet.getEvent());
        Assert.assertTrue(finalBet.getBroadcast().contains("暗注"));
        Assert.assertEquals(1, finalBet.getFinalBetPools().stream()
                .filter(pool -> dogId.equals(pool.getDogId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应下发暗注奖池"))
                .getChampionCount());

        DogRaceDTO tile = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.PLACE_TILE_REQ, null, null, 8, "bone"));
        Assert.assertEquals(DogRaceDTO.Event.PLACE_TILE, tile.getEvent());
        Assert.assertEquals(1, tile.getTiles().size());

        DogRaceDTO duplicateTile = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.PLACE_TILE_REQ, null, null, 10, "mud"));
        Assert.assertEquals(DogRaceDTO.Event.ERROR, duplicateTile.getEvent());

        DogRaceDTO roll = DogRaceService.applyRequestForTest(
                room,
                "account:2",
                "小李",
                request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));
        Assert.assertTrue(roll.getEvent() == DogRaceDTO.Event.ROLL || roll.getEvent() == DogRaceDTO.Event.RACE_SETTLE);
        Assert.assertEquals(5, roll.getParticipants().size());
    }

    @Test
    public void simulateRaceShouldBroadcastBetSettlementPreview() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-bet-settle");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        DogRaceDTO snapshot = DogRaceService.startRaceForTest(room, 20260617L);
        String firstDogId = snapshot.getParticipants().get(0).getDogId();
        DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_LEG_REQ, firstDogId, null, 0, null));
        DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_FINAL_REQ, firstDogId, "champion", 0, null));

        DogRaceDTO last = null;
        for (int i = 0; i < 80; i++) {
            last = DogRaceService.applyRequestForTest(
                    room,
                    "account:2",
                    "小李",
                    request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));
            if (last.getEvent() == DogRaceDTO.Event.RACE_SETTLE) {
                break;
            }
        }

        Assert.assertNotNull(last);
        Assert.assertEquals(DogRaceDTO.Event.RACE_SETTLE, last.getEvent());
        Assert.assertTrue(last.getBroadcasts().stream().anyMatch(line -> line.contains("暗注")));
        Assert.assertTrue(last.getRankings().stream().anyMatch(ranking -> ranking.getRewardBones() != null));
    }

    @Test
    public void hurryRollShouldUseSharedCooldown() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-hurry-cooldown");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        DogRaceService.startRaceForTest(room, 20260617L);
        DogRaceDTO first = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null),
                10000L,
                true);
        DogRaceDTO second = DogRaceService.applyRequestForTest(
                room,
                "account:2",
                "小李",
                request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null),
                12000L,
                true);
        DogRaceDTO third = DogRaceService.applyRequestForTest(
                room,
                "account:2",
                "小李",
                request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null),
                16000L,
                true);

        Assert.assertNotEquals(DogRaceDTO.Event.ERROR, first.getEvent());
        Assert.assertEquals(DogRaceDTO.Event.ERROR, second.getEvent());
        Assert.assertNotEquals(DogRaceDTO.Event.ERROR, third.getEvent());
    }

    @Test
    public void startRaceShouldScheduleAutoRollAndClearRoomShouldStopIt() {
        GameRoom room = new GameRoom();
        room.setId("dog-race-auto-roll");
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");

        DogRaceService.startRaceForTest(room, 20260617L);

        Assert.assertTrue(DogRaceService.hasAutoRollScheduledForTest(room.getId()));
        DogRaceService.clearRoom(room.getId());
        Assert.assertFalse(DogRaceService.hasAutoRollScheduledForTest(room.getId()));
    }

    @Test
    public void ownedDogRaceShouldUseRealPetDogIdAndApplyRaceResult() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-pet-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990101L);
            PetProfileDTO profile = adoptUnlockedShiba(user, "真狗");
            String realDogId = findDogId(profile, "真狗");
            GameRoom room = new GameRoom();
            room.setId("dog-race-owned-dog");
            room.setGame(Game.DOG_RACE);
            room.setNums(6);
            room.setDogRaceMode("owned_dog");
            room.getUsers().put(user.getIdentityKey(), new GameRoom.Player(
                    user.getIdentityKey(),
                    user.getId(),
                    user.getUsername(),
                    user.getAccountId(),
                    user.getAccount(),
                    user.getUuid(),
                    user.getNickname(),
                    false));

            List<DogRaceDTO> events = DogRaceService.simulateRace(room, 20260617L);
            DogRaceDTO settle = events.get(events.size() - 1);
            PetProfileDTO afterRace = PetService.profile(user);

            Assert.assertEquals(DogRaceDTO.Event.RACE_SETTLE, settle.getEvent());
            Assert.assertTrue(settle.getParticipants().stream().anyMatch(dog -> realDogId.equals(dog.getDogId())));
            Assert.assertTrue(settle.getRankings().stream().anyMatch(rank -> realDogId.equals(rank.getDogId())
                    && user.getIdentityKey().equals(rank.getOwnerPlayerKey())));
            Assert.assertEquals(1, afterRace.getDogs().get(0).getRaceCount());
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void ownedDogRaceStartShouldSpendSignupCost() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-signup-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990106L);
            PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "报名参赛狗"));
            String realDogId = profile.getDogs().get(0).getId();
            GameRoom room = ownedDogRoom("dog-race-owned-signup", user);

            DogRaceDTO snapshot = DogRaceService.startRaceForTest(room, 20260617L, true);
            PetProfileDTO afterSignup = PetService.profile(user);

            Assert.assertTrue(snapshot.getParticipants().stream().anyMatch(dog -> realDogId.equals(dog.getDogId())));
            Assert.assertEquals(280, afterSignup.getAssets().getBones());
            Assert.assertEquals(7, afterSignup.getAssets().getEnergy());
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void ownedDogRaceShouldGrantRankRewardBones() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-rank-reward-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990107L);
            PetProfileDTO profile = PetService.adopt(user, adopt("corgi", "领奖狗"));
            String realDogId = profile.getDogs().get(0).getId();
            GameRoom room = ownedDogRoom("dog-race-owned-rank-reward", user);

            DogRaceDTO settle = DogRaceService.simulateRace(room, 20260617L)
                    .stream()
                    .filter(event -> event.getEvent() == DogRaceDTO.Event.RACE_SETTLE)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("赛跑应完成"));
            int rewardBones = settle.getRankings().stream()
                    .filter(ranking -> realDogId.equals(ranking.getDogId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("真实狗应参与排名"))
                    .getRewardBones();
            int weeklyPoints = settle.getRankings().stream()
                    .filter(ranking -> realDogId.equals(ranking.getDogId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("真实狗应参与排名"))
                    .getWeeklyPoints();

            PetProfileDTO afterSettle = PetService.profile(user);
            Assert.assertEquals(300 + rewardBones, afterSettle.getAssets().getBones());
            Assert.assertEquals(weeklyPoints, afterSettle.getDogs().get(0).getWeeklyPoints());
            Assert.assertTrue(settle.getBroadcasts().stream().anyMatch(line -> line.contains("名次奖")));
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void finalBetShouldSpendBonesAndPersistRewardWhenHit() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-bet-bones-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990103L);
            PetService.profile(user);
            GameRoom previewRoom = pureBettingRoom("dog-race-final-bet-preview", user);
            DogRaceDTO previewSettle = DogRaceService.simulateRace(previewRoom, 20260617L)
                    .stream()
                    .filter(event -> event.getEvent() == DogRaceDTO.Event.RACE_SETTLE)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("赛跑应完成"));
            String championDogId = previewSettle.getRankings().get(0).getDogId();

            GameRoom room = pureBettingRoom("dog-race-final-bet-bones", user);
            DogRaceService.startRaceForTest(room, 20260617L);
            DogRaceDTO bet = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.BET_FINAL_REQ, championDogId, "champion", 0, null));

            Assert.assertEquals(DogRaceDTO.Event.BET_FINAL, bet.getEvent());
            Assert.assertEquals(280, PetService.profile(user).getAssets().getBones());

            DogRaceDTO last = null;
            for (int i = 0; i < 80; i++) {
                last = DogRaceService.applyRequestForTest(
                        room,
                        user.getIdentityKey(),
                        user.getUsername(),
                        request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));
                if (last.getEvent() == DogRaceDTO.Event.RACE_SETTLE) {
                    break;
                }
            }

            Assert.assertNotNull(last);
            Assert.assertEquals(DogRaceDTO.Event.RACE_SETTLE, last.getEvent());
            Assert.assertEquals(380, PetService.profile(user).getAssets().getBones());
            Assert.assertTrue(last.getBroadcasts().stream().anyMatch(line -> line.contains("暗注命中，获得 🦴100")));
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void finalBetShouldRejectWhenBonesAreNotEnough() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-bet-insufficient-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990104L);
            PetService.profile(user);
            PetService.changeBones(user.getAccountId(), -290);
            GameRoom room = pureBettingRoom("dog-race-final-bet-insufficient", user);
            DogRaceDTO snapshot = DogRaceService.startRaceForTest(room, 20260617L);
            String dogId = snapshot.getParticipants().get(0).getDogId();

            DogRaceDTO bet = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.BET_FINAL_REQ, dogId, "champion", 0, null));

            Assert.assertEquals(DogRaceDTO.Event.ERROR, bet.getEvent());
            Assert.assertEquals("骨头币不足", bet.getMessage());
            Assert.assertEquals(10, PetService.profile(user).getAssets().getBones());
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void boneTileShouldRewardTileOwnerWhenDogStepsOnIt() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-bone-tile-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990105L);
            PetService.profile(user);
            BoneTileScenario scenario = findBoneTileScenario(user);
            GameRoom room = pureBettingRoom("dog-race-bone-tile", user);
            DogRaceService.startRaceForTest(room, scenario.seed);

            DogRaceDTO tile = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.PLACE_TILE_REQ, null, null, scenario.cell, "bone"));
            DogRaceDTO roll = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));

            Assert.assertEquals(DogRaceDTO.Event.PLACE_TILE, tile.getEvent());
            Assert.assertNotEquals(DogRaceDTO.Event.ERROR, roll.getEvent());
            Assert.assertEquals(305, PetService.profile(user).getAssets().getBones());
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    @Test
    public void mudTileShouldRewardTileOwnerWhenDogStepsOnIt() throws Exception {
        Path tempDir = Files.createTempDirectory("xechat-dog-race-mud-tile-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, tempDir.toString());
        GlobalConfig.initDataPath(tempDir.toString());
        resetDbFactory();
        try {
            cn.xeblog.commons.entity.User user = accountUser(990108L);
            PetService.profile(user);
            BoneTileScenario scenario = findTileScenario(user, null);
            GameRoom room = pureBettingRoom("dog-race-mud-tile", user);
            DogRaceService.startRaceForTest(room, scenario.seed);

            DogRaceDTO tile = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.PLACE_TILE_REQ, null, null, scenario.cell, "mud"));
            DogRaceDTO roll = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    request(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));

            Assert.assertEquals(DogRaceDTO.Event.PLACE_TILE, tile.getEvent());
            Assert.assertNotEquals(DogRaceDTO.Event.ERROR, roll.getEvent());
            Assert.assertEquals(305, PetService.profile(user).getAssets().getBones());
            Assert.assertTrue(roll.getBroadcast().contains("泥坑"));
        } finally {
            resetDbFactory();
            System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
            GlobalConfig.initDataPath(null);
        }
    }

    private DogRaceDTO request(GameRoom room, DogRaceDTO.Event event, String dogId, String betKind, int cell, String tileType) {
        DogRaceDTO dto = new DogRaceDTO(room.getId());
        dto.setEvent(event);
        dto.setDogId(dogId);
        dto.setBetKind(betKind);
        dto.setCell(cell);
        dto.setTileType(tileType);
        return dto;
    }

    private static cn.xeblog.commons.entity.User accountUser(long accountId) {
        cn.xeblog.commons.entity.User user = new cn.xeblog.commons.entity.User();
        user.setId("dog-race-user-" + accountId);
        user.setAccountId(accountId);
        user.setAccount("dog_race_" + accountId);
        user.setNickname("赛跑狗主人");
        user.setGuest(false);
        return user;
    }

    private static PetAdoptDTO adopt(String breed, String name) {
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setBreed(breed);
        dto.setName(name);
        return dto;
    }

    private static PetProfileDTO adoptUnlockedShiba(cn.xeblog.commons.entity.User user, String name) {
        insertCheckins(user.getAccountId(), 30);
        PetService.adopt(user, adopt("shiba", name));
        return PetService.profile(user);
    }

    private static String findDogId(PetProfileDTO profile, String name) {
        for (PetDogDTO dog : profile.getDogs()) {
            if (name.equals(dog.getName())) {
                return dog.getId();
            }
        }
        Assert.fail("未找到测试狗狗: " + name);
        return null;
    }

    private static void insertCheckins(long accountId, int count) {
        long now = System.currentTimeMillis();
        LocalDate startDate = LocalDate.now().minusDays(count);
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT OR IGNORE INTO pet_checkins (account_id, checkin_date, cycle_day, created_at) " +
                             "VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < count; i++) {
                statement.setLong(1, accountId);
                statement.setString(2, startDate.plusDays(i).toString());
                statement.setInt(3, i + 1);
                statement.setLong(4, now + i);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static GameRoom ownedDogRoom(String roomId, cn.xeblog.commons.entity.User user) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("owned_dog");
        room.getUsers().put(user.getIdentityKey(), new GameRoom.Player(
                user.getIdentityKey(),
                user.getId(),
                user.getUsername(),
                user.getAccountId(),
                user.getAccount(),
                user.getUuid(),
                user.getNickname(),
                false));
        return room;
    }

    private static GameRoom pureBettingRoom(String roomId, cn.xeblog.commons.entity.User user) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.DOG_RACE);
        room.setNums(6);
        room.setDogRaceMode("pure_betting");
        room.getUsers().put(user.getIdentityKey(), new GameRoom.Player(
                user.getIdentityKey(),
                user.getId(),
                user.getUsername(),
                user.getAccountId(),
                user.getAccount(),
                user.getUuid(),
                user.getNickname(),
                false));
        return room;
    }

    private static BoneTileScenario findBoneTileScenario(cn.xeblog.commons.entity.User user) {
        return findTileScenario(user, null);
    }

    private static BoneTileScenario findTileScenario(cn.xeblog.commons.entity.User user, String breed) {
        for (long seed = 1L; seed <= 500L; seed++) {
            GameRoom room = pureBettingRoom("dog-race-bone-preview-" + seed, user);
            DogRaceDTO snapshot = DogRaceService.startRaceForTest(room, seed);
            DogRaceDTO roll = DogRaceService.applyRequestForTest(
                    room,
                    user.getIdentityKey(),
                    user.getUsername(),
                    requestStatic(room, DogRaceDTO.Event.ROLL_REQ, null, null, 0, null));
            if (roll.getDie() == null || roll.getDie().getDogId() == null) {
                DogRaceService.clearRoom(room.getId());
                continue;
            }
            String dogId = roll.getDie().getDogId();
            int targetCell = -1;
            String targetBreed = null;
            for (DogRaceDTO.Participant dog : roll.getParticipants()) {
                if (dogId.equals(dog.getDogId())) {
                    targetCell = dog.getPosition();
                    targetBreed = dog.getBreed();
                    break;
                }
            }
            if (targetCell >= 2 && targetCell <= 15
                    && (breed == null || breed.equals(targetBreed))
                    && !hasUnitAtSnapshot(snapshot, targetCell)) {
                DogRaceService.clearRoom(room.getId());
                return new BoneTileScenario(seed, targetCell);
            }
            DogRaceService.clearRoom(room.getId());
        }
        throw new AssertionError("500 个种子内应找到可测试的骨头地块落点");
    }

    private static boolean hasUnitAtSnapshot(DogRaceDTO snapshot, int cell) {
        for (DogRaceDTO.Participant dog : snapshot.getParticipants()) {
            if (dog.getPosition() == cell) {
                return true;
            }
        }
        for (DogRaceDTO.Cat cat : snapshot.getCats()) {
            if (cat.getPosition() == cell) {
                return true;
            }
        }
        return false;
    }

    private static int findParticipantPosition(DogRaceDTO dto, String dogId) {
        for (DogRaceDTO.Participant dog : dto.getParticipants()) {
            if (dogId.equals(dog.getDogId())) {
                return dog.getPosition();
            }
        }
        return -1;
    }

    private static DogRaceDTO requestStatic(GameRoom room, DogRaceDTO.Event event, String dogId, String betKind, int cell, String tileType) {
        DogRaceDTO dto = new DogRaceDTO(room.getId());
        dto.setEvent(event);
        dto.setDogId(dogId);
        dto.setBetKind(betKind);
        dto.setCell(cell);
        dto.setTileType(tileType);
        return dto;
    }

    private static class BoneTileScenario {
        private final long seed;
        private final int cell;

        private BoneTileScenario(long seed, int cell) {
            this.seed = seed;
            this.cell = cell;
        }
    }

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }
}
