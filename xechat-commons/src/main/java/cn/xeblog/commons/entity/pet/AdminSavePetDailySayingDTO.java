package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员保存狗狗每日问候内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSavePetDailySayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetDailySayingContentDTO content;

}
