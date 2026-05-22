package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 个人资料变更广播。
 *
 * <p>昵称变更或头像变更时,服务端广播给所有在线用户,
 * 客户端据此刷新本地昵称映射、头像 ?v= 缓存。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdatedDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 变更账号 ID
     */
    private long accountId;

    /**
     * 变更后的昵称(若仅头像变更,回填当前昵称)
     */
    private String nickname;

    /**
     * 变更后的头像版本(若仅昵称变更,回填当前版本)
     */
    private int avatarVersion;

}
