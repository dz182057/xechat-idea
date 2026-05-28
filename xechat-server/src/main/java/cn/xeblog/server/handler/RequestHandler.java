package cn.xeblog.server.handler;

import cn.hutool.core.thread.GlobalThreadPool;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Request;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.chess.ChessDTO;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.entity.game.landlords.LandlordsGameDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Protocol;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.action.handler.ActionHandler;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.factory.ActionHandlerFactory;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author anlingyi
 * @date 2020/8/14
 */
public class RequestHandler {

    private final ChannelHandlerContext ctx;

    private final Request request;

    public RequestHandler(final ChannelHandlerContext ctx, final Request request) {
        this.ctx = ctx;
        this.request = request;
    }

    public void exec() {
        if (request.getAction() == null) {
            return;
        }
        // 心跳：回声一条 HEARTBEAT 给发送方，作为链路存活的下行证据
        // （桌面端 WebSocket 客户端用"60s 内是否收到下行"做看门狗判断，
        // 不回声会导致空闲房间每分钟触发一次重连）
        if (request.getAction() == Action.HEARTBEAT) {
            ctx.writeAndFlush(ResponseBuilder.build(null, null, MessageType.HEARTBEAT));
            return;
        }

        // 部分拉取类 action 是无参请求，允许 body 为空。
        if (!isNoBodyAction(request.getAction()) && ObjectUtil.isEmpty(request.getBody())) {
            ctx.writeAndFlush(ResponseBuilder.system("Body is null!"));
            return;
        }

        GlobalThreadPool.execute(() -> {
            ActionHandler produce = ActionHandlerFactory.INSTANCE.produce(request.getAction());
            Object body = request.getBody();

            // 无参 action 的 body 可能为空，直接走 handler 不做反序列化。
            if (isNoBodyAction(request.getAction())) {
                produce.handle(ctx, body);
                return;
            }

            // 非默认协议需要转换body的数据类型
            if (request.getProtocol() != Protocol.DEFAULT) {
                try {
                    if (request.getAction() == Action.SET_STATUS) {
                        body = UserStatus.valueOf(body.toString());
                    } else if (request.getAction() == Action.GAME || request.getAction() == Action.GAME_OVER) {
                        // GameDTO 是多态基类，handler 泛型只声明到基类，
                        // 用基类反序列化会丢掉子类字段（如 GobangDTO.x/y/type）。
                        // 这里先按 game 字段识别具体子类，再二次反序列化保留所有字段。
                        String json = body.toString();
                        GameDTO base = JSONUtil.toBean(json, GameDTO.class);
                        Class<? extends GameDTO> subClass = resolveGameSubClass(base == null ? null : base.getGame());
                        body = subClass == GameDTO.class ? base : JSONUtil.toBean(json, subClass);
                    } else {
                        body = JSONUtil.toBean(body.toString(), ClassUtil.getTypeArgument(produce.getClass()));
                    }
                } catch (Exception e) {
                    ctx.writeAndFlush(ResponseBuilder.system("消息内容解析异常!"));
                    return;
                }
            }

            produce.handle(ctx, body);
        });
    }

    private static Class<? extends GameDTO> resolveGameSubClass(Game game) {
        if (game == null) {
            return GameDTO.class;
        }
        switch (game) {
            case GOBANG:
                return GobangDTO.class;
            case DRAW_GUESS:
                return DrawGuessDTO.class;
            case CHINESE_CHESS:
                return ChessDTO.class;
            case LANDLORDS:
                return LandlordsGameDTO.class;
            default:
                return GameDTO.class;
        }
    }

    private static boolean isNoBodyAction(Action action) {
        return action == Action.LIST_USERS
                || action == Action.ADMIN_LIST_DRAW_GUESS_WORDS
                || action == Action.DRAW_GUESS_RANDOM_WORD;
    }

}
