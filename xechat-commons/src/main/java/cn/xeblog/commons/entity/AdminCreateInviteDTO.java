package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员创建邀请码请求。
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateInviteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 最大使用次数(默认 1,0 表示无限)
     */
    private Integer maxUses;

    /**
     * 多少天后过期(null 表示永久)
     */
    private Integer expiresInDays;

    /**
     * 备注(发给谁等)
     */
    private String note;

}
