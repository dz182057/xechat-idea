package cn.xeblog.plugin.handler;

import cn.xeblog.commons.entity.LoginDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.plugin.action.ConnectionAction;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.plugin.persistence.PersistenceService;
import cn.xeblog.plugin.ui.MainWindow;
import cn.xeblog.plugin.util.IdeaUtils;
import cn.xeblog.commons.entity.GuestLoginDTO;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.commons.lang3.StringUtils;

/**
 * @author anlingyi
 * @date 2020/5/29
 */
public class XEChatClientHandler extends SimpleChannelInboundHandler<Response> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        DataCache.channel = ctx.channel();
        DataCache.isOnline = true;

        boolean reconnected = DataCache.reconnected;
        if (!reconnected) {
            ConsoleAction.clean();
            ConsoleAction.showSimpleMsg("正在登录中...");
        }

        UserStatus status = DataCache.userStatus;
        if (status == null) {
            status = UserStatus.FISHING;
        }
        if (GameAction.playing()) {
            status = UserStatus.PLAYING;
        }

        // 三路分发(account+password / token / guest username):
        // 1) account+password → LOGIN(账号登录)
        // 2) account 与本地持久化匹配 + 有 token → LOGIN_WITH_TOKEN
        // 3) 否则 → GUEST_LOGIN(由 LoginCommandHandler 设 guestMode=true)
        if (StringUtils.isNotBlank(DataCache.account) && StringUtils.isNotBlank(DataCache.password)) {
            LoginDTO dto = new LoginDTO();
            dto.setAccount(DataCache.account);
            dto.setPassword(DataCache.password);
            dto.setStatus(status);
            dto.setReconnected(reconnected);
            dto.setPluginVersion(IdeaUtils.getPluginVersion());
            dto.setUuid(DataCache.uuid);
            dto.setPlatform(Platform.IDEA);
            MessageAction.send(dto, Action.LOGIN);
            // 不在此处清 DataCache.password:LoginResultMessageHandler 还要用它
            // 派生 E2EE master key 并解包身份私钥,派生完后立刻清。
        } else if (StringUtils.isNotBlank(DataCache.account)) {
            String token = PersistenceService.getData().getToken();
            String savedAccount = PersistenceService.getData().getAccount();
            if (StringUtils.isNotBlank(token) && DataCache.account.equals(savedAccount)) {
                LoginDTO dto = new LoginDTO();
                dto.setToken(token);
                dto.setStatus(status);
                dto.setReconnected(reconnected);
                dto.setPluginVersion(IdeaUtils.getPluginVersion());
                dto.setUuid(DataCache.uuid);
                dto.setPlatform(Platform.IDEA);
                MessageAction.send(dto, Action.LOGIN_WITH_TOKEN);
            } else {
                ConsoleAction.showSimpleMsg("缺少 token,请重新执行 #login <账号> <密码>");
                ctx.close();
                return;
            }
        } else {
            // 游客模式
            GuestLoginDTO dto = new GuestLoginDTO();
            dto.setNickname(DataCache.username);
            dto.setUuid(DataCache.uuid);
            dto.setPlatform(Platform.IDEA);
            dto.setPluginVersion(IdeaUtils.getPluginVersion());
            MessageAction.send(dto, Action.GUEST_LOGIN);
        }
        DataCache.reconnected = false;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Response msg) throws Exception {
        new ResponseHandler(msg).exec();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ConsoleAction.showSimpleMsg("你干嘛~ 哎哟！");
        cause.printStackTrace();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        DataCache.isOnline = false;
        DataCache.userMap = null;
        if (!GameAction.isOfflineGame()) {
            GameAction.over();
        }

        ConsoleAction.showSimpleMsg("已断开连接！");
        ConsoleAction.setConsoleTitle("控制台");

        if (DataCache.reconnected) {
            ConnectionAction connectionAction = DataCache.connectionAction;
            if (connectionAction == null) {
                connectionAction = new ConnectionAction();
            }

            ConsoleAction.showSimpleMsg("正在重新连接服务器...");
            connectionAction.exec(null);
        } else {
            // 非重连场景的断开(显式登出、服务端踢、心跳超时无法恢复) → 兜底切回登录页
            MainWindow mw = MainWindow.getInstance();
            mw.switchToLogin();
            // 登录阶段被断开(SYSTEM 错误后服务端 close): 兜底让登录页恢复输入,
            // 防止只 close 不发 SYSTEM(或 SYSTEM 已显示但状态没清)时一直卡 loading
            if (mw.getLoginPanel() != null && mw.getLoginPanel().isAwaitingLogin()) {
                mw.getLoginPanel().showError("连接已断开,请检查后重试");
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            switch (event.state()) {
                case WRITER_IDLE:
                    MessageAction.send(null, Action.HEARTBEAT);
                    break;
                case READER_IDLE:
                    break;
                case ALL_IDLE:
                    ctx.close();
                    DataCache.reconnected = true;
            }
        }
    }
}
