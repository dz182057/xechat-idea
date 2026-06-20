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

    private List<PetCollectionItemDTO> collections;

    private String companionDogId;

    private PetCheckinStatusDTO checkinStatus;

    private PetExploreStatusDTO exploreStatus;

    private PetInteractionStatusDTO interactionStatus;

    private PetTrainingStatusDTO trainingStatus;

    public static PetProfileDTO empty(long accountId) {
        PetProfileDTO profile = new PetProfileDTO();
        profile.setAccountId(accountId);
        profile.setAssets(new PetAssetsDTO(300, 6, 0, 1, 10));
        profile.setDogs(new ArrayList<>());
        profile.setItems(new ArrayList<>());
        profile.setCollections(new ArrayList<>());
        profile.setExploreStatus(new PetExploreStatusDTO(3, 0, 5, 0, 0,
                false, false, false, 0, 0));
        profile.setInteractionStatus(new PetInteractionStatusDTO(150, 0, 150, 2,
                new java.util.HashMap<>(), new java.util.HashMap<>()));
        profile.setTrainingStatus(new PetTrainingStatusDTO("pending",
                java.util.Arrays.asList(100, 150, 300, 500, 800),
                new ArrayList<>(), new ArrayList<>(), false));
        return profile;
    }

}
