package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗领养请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetAdoptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String breed;

    private String name;

}
