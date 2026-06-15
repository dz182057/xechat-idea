package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 狗狗探险开箱结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreOpenResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetProfileDTO profile;

    private List<PetExploreRewardDTO> rewards;

}
