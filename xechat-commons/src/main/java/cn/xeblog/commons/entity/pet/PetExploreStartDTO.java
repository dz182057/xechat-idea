package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗探险开始请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreStartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dogId;

    private String location;

    private int durationHours;

}
