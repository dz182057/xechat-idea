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

    private String copyContent;

    private UserMsgDTO.MsgType msgType;

    private RecallMessageDTO.ConversationType conversationType;

    private long createdAt;

    private boolean recalled;

    private int startOffset;

    private int endOffset;

    private JLabel imageLabel;

    private String imageFileName;

    private String imageFilePath;

    public boolean canQuote() {
        return messageId != null && !recalled;
    }

    public boolean canRecall(String currentUsername) {
        return messageId != null
                && !recalled
                && user != null
                && currentUsername != null
                && currentUsername.equals(user.getUsername())
                && System.currentTimeMillis() - createdAt <= 2 * 60 * 1000;
    }

}
