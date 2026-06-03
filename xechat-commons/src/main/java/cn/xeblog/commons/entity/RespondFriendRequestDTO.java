package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 处理好友申请请求。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespondFriendRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long requestId;

    private boolean accepted;

}
