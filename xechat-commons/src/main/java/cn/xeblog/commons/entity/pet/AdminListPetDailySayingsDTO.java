package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员查询狗狗每日问候内容库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminListPetDailySayingsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String category;
    private String reviewStatus;
    private String keyword;
    private Boolean active;
    private int page;
    private int pageSize;

}
