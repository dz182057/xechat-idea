package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

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

        Assert.assertEquals("990001", profile.getAccountId());
        Assert.assertEquals(100, profile.getAssets().getBones());
        Assert.assertEquals(1, profile.getAssets().getDogSlots());
        Assert.assertTrue(profile.getDogs().isEmpty());
        Assert.assertNotNull(profile.getCheckinStatus().getServerDate());
    }

    @Test
    public void adoptShouldCreateFirstDogAndRespectSlotLimit() {
        User user = accountUser(990002L);

        PetProfileDTO profile = PetService.adopt(user, adopt("shiba", "小白"));

        Assert.assertEquals(1, profile.getDogs().size());
        PetProfileDTO.Dog dog = profile.getDogs().get(0);
        Assert.assertEquals("小白", dog.getName());
        Assert.assertEquals("shiba", dog.getBreed());
        Assert.assertEquals("puppy", dog.getStage());
        Assert.assertEquals(10, dog.getEnergy());

        try {
            PetService.adopt(user, adopt("corgi", "小黄"));
            Assert.fail("狗位已满时不应继续领养");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("狗位已满", e.getMessage());
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

        PetProfileDTO.Dog dog = afterThirdRace.getDogs().get(0);
        Assert.assertEquals(3, dog.getRaceCount());
        Assert.assertEquals(1, dog.getRaceFirstCount());
        Assert.assertEquals("adult", dog.getStage());
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
        PetRaceResultDTO dto = new PetRaceResultDTO();
        dto.setDogId(dogId);
        dto.setRank(rank);
        return dto;
    }

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }
}
