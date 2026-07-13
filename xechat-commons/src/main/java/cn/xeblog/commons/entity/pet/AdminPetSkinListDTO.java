package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员狗狗之家皮肤目录响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetSkinListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<AdminPetSkinDTO> skins = new ArrayList<>();

}
