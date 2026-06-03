package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 好友列表消息。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendListMsgDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FriendDTO> friends;

}
