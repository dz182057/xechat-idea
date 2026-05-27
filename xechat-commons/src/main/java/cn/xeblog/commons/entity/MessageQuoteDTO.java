package cn.xeblog.commons.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息引用摘要。
 *
 * @author dz
 * @date 2026/5/27
 */
@Data
@NoArgsConstructor
public class MessageQuoteDTO implements Serializable {

    private Long messageId;

    private String sender;

    private UserMsgDTO.MsgType msgType;

    private String content;

}
