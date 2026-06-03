package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 待处理好友申请列表消息。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestListMsgDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FriendRequestDTO> requests;

}
