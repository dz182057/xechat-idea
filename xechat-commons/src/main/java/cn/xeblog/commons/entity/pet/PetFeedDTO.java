package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗喂食请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFeedDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dogId;

}
