package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员狗狗每日问候内容库列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetDailySayingContentListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<PetDailySayingContentDTO> records;
    private int total;
    private int page;
    private int pageSize;
    private List<String> categories;
    private String contentVersion;

}
