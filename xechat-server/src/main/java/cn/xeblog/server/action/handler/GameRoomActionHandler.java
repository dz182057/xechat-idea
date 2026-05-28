package cn.xeblog.server.action.handler;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.*;
import cn.xeblog.commons.entity.game.GameInviteDTO;
import cn.xeblog.commons.entity.game.GameInviteResultDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.InviteStatus;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.game.quickquiz.QuickQuizService;

/**
 * @author anlingyi
 * @date 2022/5/25 3:26 下午
 */
@DoAction(Action.GAME_ROOM)
public class GameRoomActionHandler extends AbstractGameActionHandler<GameRoomMsgDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, GameRoomMsgDTO body) {
        switch (body.getMsgType()) {
            case PLAYER_LEFT:
                playerLeft(user, gameRoom, body);
                break;
            case PLAYER_INVITE:
                playerInvite(user, gameRoom, body);
                break;
            case PLAYER_INVITE_RESULT:
                playerInviteResult(user, gameRoom, body);
                break;
            case ROOM_CLOSE:
                roomClose(user, gameRoom);
                break;
            case GAME_START:
                gameRoom.getUsers().forEach((k, v) -> v.setReadied(false));
                body.setContent(gameRoom);
                sendMsg(gameRoom, ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                break;
            case PLAYER_READY:
                gameRoom.readied(user);
                sendMsg(gameRoom, ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                break;
            case PLAYER_CANCEL_READY:
                gameRoom.readyCancelled(user);
                sendMsg(gameRoom, ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                break;
            case GAME_OVER:
                user.send(ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                break;
            case PLAYER_GAME_STARTED:
                gameRoom.getHomeowner().send(ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                break;
            case REGRET_REQUEST:
            case REGRET_RESPONSE:
                // 悔棋协商：服务端不参与决策，只把消息原样转发给房间内其他玩家
                gameRoom.getUsers().forEach((k, v) -> {
                    if (v.getId().equals(user.getId())) {
                        return;
                    }
                    User player = UserCache.get(v.getId());
                    if (player != null) {
                        player.send(ResponseBuilder.build(user, body, MessageType.GAME_ROOM));
                    }
                });
                break;
        }
    }

    private void roomClose(User user, GameRoom gameRoom) {
        GameRoomCache.removeRoom(gameRoom.getId());
        QuickQuizService.clearRoom(gameRoom.getId());

        GameRoomMsgDTO msg = new GameRoomMsgDTO();
        msg.setRoomId(gameRoom.getId());
        msg.setMsgType(GameRoomMsgDTO.MsgType.ROOM_CLOSE);

        Response resp = ResponseBuilder.build(user, msg, MessageType.GAME_ROOM);
        // 通知已收到游戏邀请但还未进入游戏房间的用户
        gameRoom.getInviteUsers().forEach(player -> player.send(resp));
        // 通知房间内的用户
        sendMsg(gameRoom, resp);
    }

    private void playerInviteResult(User user, GameRoom gameRoom, GameRoomMsgDTO body) {
        // 兼容 WebSocket+JSON 客户端：hutool 反序列化时 content 会变成 JSONObject，
        // 这里再做一次 toBean 转换，TCP+Protostuff 客户端走 instanceof 分支不受影响
        GameInviteResultDTO dto = castContent(body, GameInviteResultDTO.class);
        // 转换后的 dto 与 body.content 解耦，后续 setGameRoom 等需要回写
        body.setContent(dto);
        User player = user;
        if (dto.getPlayerId() != null) {
            player = UserCache.get(dto.getPlayerId());
            if (player == null) {
                return;
            }
        }

        gameRoom.removeInviteUser(player);
        Response response = ResponseBuilder.build(player, body, MessageType.GAME_ROOM);
        if (dto.getStatus() == InviteStatus.ACCEPT) {
            if (GameRoomCache.joinRoom(gameRoom.getId(), player)) {
                player.setStatus(UserStatus.PLAYING);
                ChannelAction.updateUserStatus(player);
                dto.setGameRoom(gameRoom);
                sendMsg(gameRoom, response);
            } else {
                player.setStatus(UserStatus.FISHING);
                ChannelAction.updateUserStatus(player);
                player.send(ResponseBuilder.build(null, new GameRoomMsgDTO(GameRoomMsgDTO.MsgType.GAME_ERROR, "加入游戏失败，游戏房间已满员！"), MessageType.GAME_ROOM));
            }
        } else {
            if (player.getStatus() == UserStatus.PLAYING) {
                player.setStatus(UserStatus.FISHING);
                ChannelAction.updateUserStatus(player);
            }
            // 通知房主
            gameRoom.getHomeowner().send(response);
            if (dto.getStatus() == InviteStatus.TIMEOUT) {
                // 通知玩家游戏邀请超时
                player.send(response);
            }
        }
    }

    private void playerInvite(User user, GameRoom gameRoom, GameRoomMsgDTO body) {
        GameInviteDTO dto = castContent(body, GameInviteDTO.class);
        User player = UserCache.get(dto.getPlayerId());
        if (player == null) {
            user.send(ResponseBuilder.system("该邀请用户不存在！"));
            return;
        }

        GameRoomMsgDTO msg = new GameRoomMsgDTO();
        msg.setGame(gameRoom.getGame());
        msg.setRoomId(gameRoom.getId());
        if (player.getStatus() != UserStatus.FISHING) {
            msg.setMsgType(GameRoomMsgDTO.MsgType.PLAYER_INVITE_RESULT);
            msg.setContent(new GameInviteResultDTO(InviteStatus.REJECT, null, null));
            user.send(ResponseBuilder.build(player, msg, MessageType.GAME_ROOM));
            user.send(ResponseBuilder.system("人家正在" + player.getStatus().alias() + "呢！就你天天摸鱼？"));
            return;
        }
        if (gameRoom.getInviteUsers().contains(player)) {
            user.send(ResponseBuilder.system("已向" + player.getUsername() + "发送过邀请，请等待对方响应"));
            return;
        }

        gameRoom.addInviteUser(player);
        msg.setMsgType(GameRoomMsgDTO.MsgType.PLAYER_INVITE);
        player.send(ResponseBuilder.build(user, msg, MessageType.GAME_ROOM));
        user.send(ResponseBuilder.system("已向" + player.getUsername() + "发送《" + gameRoom.getGame().getName() + "》游戏邀请！"));
    }

    private void playerLeft(User user, GameRoom gameRoom, GameRoomMsgDTO body) {
        user.setStatus(UserStatus.FISHING);
        ChannelAction.updateUserStatus(user);

        if (GameRoomCache.leftRoom(gameRoom.getId(), user)) {
            QuickQuizService.clearRoom(gameRoom.getId());
            Response resp = ResponseBuilder.build(user, body, MessageType.GAME_ROOM);
            sendMsg(gameRoom, resp);
            if (gameRoom.isHomeowner(user.getUsername())) {
                roomClose(user, gameRoom);
            }
        }
    }

    /**
     * 取 GameRoomMsgDTO.content 并按目标类型转换。
     * - TCP+Protostuff 通道：content 本身就是目标类型，直接强转
     * - WebSocket+JSON 通道：content 是 hutool 反序列化的 JSONObject/Map，需要二次 toBean
     */
    @SuppressWarnings("unchecked")
    private <T> T castContent(GameRoomMsgDTO body, Class<T> clazz) {
        Object raw = body.getContent();
        if (raw == null) {
            return null;
        }
        if (clazz.isInstance(raw)) {
            return (T) raw;
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(raw), clazz);
    }

    private void sendMsg(GameRoom gameRoom, Response response) {
        gameRoom.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getId());
            if (player != null) {
                player.send(response);
            }
        });
    }

}
