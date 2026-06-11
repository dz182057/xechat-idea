package cn.xeblog.commons.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 查询当前会话历史总数请求。
 *
 * @author dz
 * @date 2026/6/10
 */
@Data
@NoArgsConstructor
public class CountHistoryDTO implements Serializable {

    private RecallMessageDTO.ConversationType conversationType;

    private String peerAccount;

}
