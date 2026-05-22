package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.annotation.DoAction;
import io.netty.channel.ChannelHandlerContext;

/**
 * 主动拉取在线用户列表。
 * <p>
 * 客户端登录时服务端会主动推一次 ONLINE_USERS，
 * 但桌面端 / Web 端可能希望在重连、切换页签等场景再次同步，
 * 因此提供一个无参 LIST_USERS action：服务端只把列表回推给请求方。
 *
 * @author dz
 * @date 2026/05/22
 */
@DoAction(Action.LIST_USERS)
public class ListUsersActionHandler implements ActionHandler<Object> {

    @Override
    public void handle(ChannelHandlerContext ctx, Object body) {
        User user = ChannelAction.getUser(ctx);
        if (user == null) {
            // 未登录用户不响应，避免把列表广播出去（sendOnlineUsers(null) 会全员广播）
            return;
        }
        // 只回给请求方
        ChannelAction.sendOnlineUsers(user);
    }

}
