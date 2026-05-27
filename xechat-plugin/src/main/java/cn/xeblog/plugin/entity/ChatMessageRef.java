package cn.xeblog.plugin.entity;

import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import lombok.Data;

import javax.swing.*;

/**
 * 插件端控制台里一条可操作消息的定位信息。
 *
 * @author dz
 * @date 2026/5/27
 */
@Data
public class ChatMessageRef {

    private Long messageId;

    private User user;

    private String summary;

    private UserMsgDTO.MsgType msgType;

    private RecallMessageDTO.ConversationType conversationType;

    private long createdAt;

    private int startOffset;

    private int endOffset;

    private JLabel imageLabel;

    public boolean canRecall(String currentUsername) {
        return messageId != null
                && user != null
                && currentUsername != null
                && currentUsername.equals(user.getUsername())
                && System.currentTimeMillis() - createdAt <= 2 * 60 * 1000;
    }

}
