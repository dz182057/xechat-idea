package cn.xeblog.commons.enums;

/**
 * 消息类型
 *
 * @author anlingyi
 * @date 2020/6/1
 */
public enum MessageType {
    /**
     * 用户消息
     */
    USER,
    /**
     * 系统消息
     */
    SYSTEM,
    /**
     * 在线用户列表消息
     */
    ONLINE_USERS,
    /**
     * 游戏数据消息
     */
    GAME,
    /**
     * 游戏结束消息
     */
    GAME_OVER,
    /**
     * 历史聊天记录消息
     */
    HISTORY_MSG,
    /**
     * 游戏房间消息
     */
    GAME_ROOM,
    /**
     * 游戏房间已创建消息
     */
    GAME_ROOM_CREATED,
    /**
     * 用户状态更新消息
     */
    STATUS_UPDATE,
    /**
     * 用户上线、离线消息
     */
    USER_STATE,
    /**
     * 心跳消息
     */
    HEARTBEAT,
    /**
     * react
     */
    REACT,
    /**
     * 个人资料变更广播(昵称/头像版本)
     */
    PROFILE_UPDATED,
    /**
     * 登录/注册结果(下行 LoginResultDTO)
     */
    LOGIN_RESULT,
    /**
     * 管理员邀请码列表响应
     */
    INVITE_LIST,
    /**
     * 管理员邀请码创建响应
     */
    INVITE_CREATED;
}
