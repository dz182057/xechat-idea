package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前会话历史总数响应。
 *
 * @author dz
 * @date 2026/6/10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryCountDTO implements Serializable {

    private RecallMessageDTO.ConversationType conversationType;

    private String peerAccount;

    private long total;

}
