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
    ADMIN_DELETE_USER,

    /**
     * 管理员:重置用户密码
     */
    ADMIN_RESET_PASSWORD,

    /**
     * 管理员:查看你画我猜词库
     */
    ADMIN_LIST_DRAW_GUESS_WORDS,

    /**
     * 管理员:保存你画我猜词库
     */
    ADMIN_SAVE_DRAW_GUESS_WORDS,

    /**
     * 你画我猜:随机获取一个词
     */
    DRAW_GUESS_RANDOM_WORD,

    /**
     * 管理员:查看快问快答题库
     */
    ADMIN_LIST_QUICK_QUIZ_QUESTIONS,

    /**
     * 管理员:保存快问快答题库
     */
    ADMIN_SAVE_QUICK_QUIZ_QUESTIONS,

    /**
     * 快问快答:查询当前房间剩余可用题数
     */
    QUICK_QUIZ_AVAILABLE_COUNT,

    /**
     * 快问快答:房主请求下一题
     */
    QUICK_QUIZ_NEXT_QUESTION,

    /**
     * 快问快答:提交答案
     */
    QUICK_QUIZ_SUBMIT_ANSWER,

    /**
     * 快问快答:查看我的答题记录
     */
    QUICK_QUIZ_MY_RECORDS,

    /**
     * 管理员:查看全部快问快答答题记录
     */
    ADMIN_LIST_QUICK_QUIZ_RECORDS,

    /**
     * 管理员:查看海龟汤题库
     */
    ADMIN_LIST_TURTLE_SOUP_STORIES,

    /**
     * 管理员:保存海龟汤题库
     */
    ADMIN_SAVE_TURTLE_SOUP_STORIES,

    /**
     * 海龟汤:房主请求下一题
     */
    TURTLE_SOUP_NEXT_STORY,

    /**
     * 海龟汤:查看我的游戏记录
     */
    TURTLE_SOUP_MY_RECORDS,

    /**
     * 管理员:查看全部海龟汤记录
     */
    ADMIN_LIST_TURTLE_SOUP_RECORDS,

    /**
     * 游客登录(无账号体系,仅大厅聊天,禁止私聊)
     */
    GUEST_LOGIN,

    /**
     * 拉取历史聊天记录(公共频道)
     */
    PULL_HISTORY,

    /**
     * 查询对端账号的身份公钥(E2EE 私聊建立会话密钥前的公钥发现)
     */
    GET_PEER_KEY,

    /**
     * 私聊加密消息(E2EE 信封,服务端只看密文)
     */
    PRIVATE_CHAT,

    /**
     * 拉取与某 peer 的私聊密文历史
     */
    PULL_PRIVATE_HISTORY,

    /**
     * 撤回消息
     */
    RECALL_MESSAGE;
}
