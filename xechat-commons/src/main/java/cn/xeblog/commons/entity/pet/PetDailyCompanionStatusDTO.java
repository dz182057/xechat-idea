package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 今日陪伴完成状态快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailyCompanionStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, PetDailyCompanionDogStatusDTO> dogs = new HashMap<>();
}
