package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发起好友申请请求。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddFriendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标账号或昵称
     */
    private String target;

}
