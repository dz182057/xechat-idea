package cn.xeblog.server.game.dograce;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
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

        DogRaceDTO legBet = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_LEG_REQ, dogId, null, 0, null));
        Assert.assertEquals(DogRaceDTO.Event.BET_LEG, legBet.getEvent());
        Assert.assertTrue(legBet.getBroadcast().contains("赛段注"));

        DogRaceDTO finalBet = DogRaceService.applyRequestForTest(
                room,
                "account:1",
                "阿明",
                request(room, DogRaceDTO.Event.BET_FINAL_REQ, dogId, "champion", 0, null));
        Assert.assertEquals(DogRaceDTO.Event.BET_FINAL, finalBet.getEvent());
        Assert.assertTrue(finalBet.getBroadcast().contains("暗注"));

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
            PetProfileDTO profile = PetService.adopt(user, adopt("shiba", "真狗"));
            String realDogId = profile.getDogs().get(0).getId();
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

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }
}
