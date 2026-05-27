package cn.xeblog.commons.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author anlingyi
 * @date 2022/5/8 5:42 下午
 */
@Data
@NoArgsConstructor
public class UserMsgDTO implements Serializable {

    private Object content;

    private MsgType msgType;

    private String[] toUsers;

    private String originalFileName;

    private MessageQuoteDTO quote;

    /**
     * 服务端落库后回填的雪花消息 ID(仅公共频道消息有,客户端用于本地缓存去重)。
     * 上行时由客户端忽略,服务端不读。
     */
    private Long serverId;

    /**
     * 服务端落库时的 epoch ms(仅公共频道消息有,客户端用于按 ts 排序与 sinceMs 增量拉取)。
     * 上行时由客户端忽略,服务端不读。
     */
    private Long serverCreatedAt;

    private Boolean recalled;

    public UserMsgDTO(Object content) {
        this(content, MsgType.TEXT);
    }

    public UserMsgDTO(Object content, String[] toUsers) {
        this(content, MsgType.TEXT, toUsers);
    }

    public UserMsgDTO(Object content, MsgType type) {
        this(content, type, null);
    }

    public UserMsgDTO(Object content, MsgType msgType, String[] toUsers) {
        this.content = content;
        this.msgType = msgType;
        this.toUsers = toUsers;
    }

    public enum MsgType {
        TEXT,
        IMAGE
    }

    public boolean hasUser(String username) {
        if (username != null && toUsers != null) {
            for (String toUser : toUsers) {
                if (toUser.equals(username)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void setMsgType(MsgType msgType) {
        this.msgType = msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = MsgType.valueOf(msgType);
    }

}
