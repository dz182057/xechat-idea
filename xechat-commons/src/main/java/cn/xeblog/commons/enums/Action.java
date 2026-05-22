package cn.xeblog.commons.enums;

/**
 * 客户端动作
 *
 * @author anlingyi
 * @date 2020/6/1
 */
public enum Action {
    /**
     * 登录
     */
    LOGIN,

    /**
     * 聊天
     */
    CHAT,

    /**
     * 游戏
     */
    GAME,

    /**
     * 设置状态
     */
    SET_STATUS,

    /**
     * 游戏结束
     */
    GAME_OVER,

    /**
     * 游戏房间
     */
    GAME_ROOM,

    /**
     * 创建游戏房间
     */
    CREATE_GAME_ROOM,

    /**
     * 在线用户列表
     */
    LIST_USERS,

    /**
     * 心跳
     */
    HEARTBEAT,

    /**
     * 查询天气
     */
    WEATHER,
    /**
     * react
     */
    REACT,

    /**
     * 注册账号
     */
    REGISTER,

    /**
     * 使用 token 登录(自动登录)
     */
    LOGIN_WITH_TOKEN,

    /**
     * 退出登录(吊销当前 token)
     */
    LOGOUT,

    /**
     * 修改个人资料(昵称/头像/密码,任一)
     */
    UPDATE_PROFILE,

    /**
     * 注销自己的账号
     */
    DELETE_ACCOUNT,

    /**
     * 管理员:生成邀请码
     */
    ADMIN_CREATE_INVITE,

    /**
     * 管理员:邀请码列表
     */
    ADMIN_LIST_INVITES,

    /**
     * 管理员:吊销邀请码
     */
    ADMIN_REVOKE_INVITE,

    /**
     * 管理员:注销指定账号
     */
    ADMIN_DELETE_USER;
}
