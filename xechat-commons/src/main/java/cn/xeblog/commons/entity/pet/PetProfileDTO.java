package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 狗狗宇宙账号资料快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long accountId;

    private PetAssetsDTO assets;

    private List<PetDogDTO> dogs;

    private List<PetInventoryItemDTO> items;

    private List<String> discoveredItemIds;

    private List<PetExploreChestDTO> exploreChests;

    private List<PetCollectionItemDTO> collections;

    private String companionDogId;

    private List<String> activeDogIds;

    private PetCheckinStatusDTO checkinStatus;

    private PetExploreStatusDTO exploreStatus;

    private PetInteractionStatusDTO interactionStatus;

    private PetDailyCompanionStatusDTO dailyCompanionStatus;

    private PetTrainingStatusDTO trainingStatus;

    private PetShopStatusDTO shopStatus;

    private PetDailySayingDTO dailySaying;

    private List<PetRecentSayingDTO> recentSayings;

    public static PetProfileDTO empty(long accountId) {
        PetProfileDTO profile = new PetProfileDTO();
        profile.setAccountId(accountId);
        profile.setAssets(new PetAssetsDTO(300, 6, 0, 1, 10,
                java.time.LocalDate.now().toString(), 10));
        profile.setDogs(new ArrayList<>());
        profile.setItems(new ArrayList<>());
        profile.setDiscoveredItemIds(new ArrayList<>());
        profile.setExploreChests(new ArrayList<>());
        profile.setCollections(new ArrayList<>());
        profile.setActiveDogIds(new ArrayList<>());
        profile.setExploreStatus(new PetExploreStatusDTO(3, 0, 5, 0, 0,
                false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null));
        profile.setInteractionStatus(new PetInteractionStatusDTO(150, 0, 150, 2,
                new java.util.HashMap<>(), new java.util.HashMap<>()));
        profile.setDailyCompanionStatus(new PetDailyCompanionStatusDTO(new java.util.HashMap<>()));
        profile.setTrainingStatus(new PetTrainingStatusDTO("pending",
                java.util.Arrays.asList(100, 150, 300, 500, 800),
                new ArrayList<>(), new ArrayList<>(), false));
        profile.setShopStatus(new PetShopStatusDTO(null, new ArrayList<>(), 0L, 0L,
                0, 3, java.util.Arrays.asList(30, 50, 70), 30));
        profile.setDailySaying(PetDailySayingDTO.none());
        profile.setRecentSayings(new ArrayList<>());
        return profile;
    }

}
