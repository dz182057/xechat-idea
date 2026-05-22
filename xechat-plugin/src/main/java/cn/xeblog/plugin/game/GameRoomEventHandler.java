package cn.xeblog.plugin.game;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.User;

/**
 * @author anlingyi
 * @date 2022/5/25 10:18 上午
 */
public interface GameRoomEventHandler {

    /**
     * 房间已创建
     *
     * @param gameRoom
     */
    void roomCreated(GameRoom gameRoom);

    /**
     * 玩家已加入
     *
     * @param player
     */
    void playerJoined(User player);

    /**
     * 玩家邀请失败
     *
     * @param player
     */
    void playerInviteFailed(User player);

    /**
     * 玩家已离开
     *
     * @param player
     */
    void playerLeft(User player);

    /**
     * 玩家已准备
     *
     * @param player
     */
    void playerReadied(User player);

    /**
     * 房间已开启
     *
     * @param gameRoom
     */
    void roomOpened(GameRoom gameRoom);

    /**
     * 房间已关闭
     */
    void roomClosed();

    /**
     * 游戏已开始
     *
     * @param gameRoom
     */
    void gameStarted(GameRoom gameRoom);

    /**
     * 玩家游戏已开始
     *
     * @param user
     */
    void playerGameStarted(User user);

    /**
     * 游戏已结束
     */
    void gameEnded();

    /**
     * 收到对方悔棋请求
     *
     * @param requester 发起方
     */
    default void onRegretRequest(User requester) {
    }

    /**
     * 收到对方对悔棋请求的响应
     *
     * @param responder 响应者
     * @param agreed    是否同意
     */
    default void onRegretResponse(User responder, boolean agreed) {
    }

}
