package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 狗狗每日问候内容库分页响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetDailySayingContentListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<PetDailySayingContentDTO> items = new ArrayList<>();
    private int total;
    private int page;
    private int pageSize;
    private String contentVersion;

}
