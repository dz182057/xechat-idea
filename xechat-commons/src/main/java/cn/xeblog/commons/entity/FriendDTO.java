package cn.xeblog.commons.entity;

import cn.xeblog.commons.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 好友列表项。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;
    private String account;
    private String nickname;
    private int avatarVersion;
    private boolean online;
    private Set<Platform> platforms;

}
