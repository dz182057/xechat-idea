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

    private String companionDogId;

    private PetCheckinStatusDTO checkinStatus;

    public static PetProfileDTO empty(long accountId) {
        PetProfileDTO profile = new PetProfileDTO();
        profile.setAccountId(accountId);
        profile.setAssets(new PetAssetsDTO(300, 6, 0, 1, 10));
        profile.setDogs(new ArrayList<>());
        profile.setItems(new ArrayList<>());
        return profile;
    }

}
