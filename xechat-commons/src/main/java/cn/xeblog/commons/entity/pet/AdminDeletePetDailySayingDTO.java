package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员下架狗狗每日问候内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDeletePetDailySayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String contentId;

}
