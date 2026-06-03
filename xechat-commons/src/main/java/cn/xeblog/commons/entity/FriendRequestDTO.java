package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 好友申请展示 DTO。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long requestId;
    private Long fromAccountId;
    private String fromAccount;
    private String fromNickname;
    private int fromAvatarVersion;
    private Long createdAt;

}
