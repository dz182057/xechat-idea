package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.commons.entity.PullPrivateHistoryDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.enums.Command;
import org.apache.commons.lang3.StringUtils;

/**
 * 拉取与某 peer 的私聊密文历史(PULL_PRIVATE_HISTORY)。
 *
 * <p>用法:#privhistory {对方昵称} [{条数}]。</p>
 *
 * <p>反查 peer.account 后发请求,响应由
 * {@link cn.xeblog.plugin.action.handler.message.PrivateHistoryMessageHandler} 解密渲染。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoCommand(Command.PRIVHISTORY)
public class PrivHistoryCommandHandler extends AbstractCommandHandler {

    @Override
    public void process(String[] args) {
        if (!DataCache.isOnline) {
            ConsoleAction.showLoginMsg();
            return;
        }
        if (DataCache.identityPrivKey == null) {
            ConsoleAction.showSimpleMsg("E2EE 私钥未解锁,请用 #exit 后用密码重登");
            return;
        }
        if (args == null || args.length == 0) {
            ConsoleAction.showSimpleMsg(Command.PRIVHISTORY.getDesc());
            return;
        }
        String peerUsername = args[0];
        User peer = DataCache.getUser(peerUsername);
        String peerAccount;
        if (peer != null && StringUtils.isNotBlank(peer.getAccount())) {
            peerAccount = peer.getAccount();
        } else {
            // 用户离线时退一步查反查表(由历史 PEER_KEY/PRIVATE_USER 维护)
            peerAccount = DataCache.peerAccountByUsername.get(peerUsername);
            if (StringUtils.isBlank(peerAccount)) {
                ConsoleAction.showSimpleMsg("找不到用户 " + peerUsername + " 的账号信息(对方可能未注册或未上线过)");
                return;
            }
        }

        int limit = 50;
        if (args.length >= 2 && args[1].matches("\\d+")) {
            limit = Math.min(200, Math.max(1, Integer.parseInt(args[1])));
        }
        PullPrivateHistoryDTO dto = new PullPrivateHistoryDTO();
        dto.setPeerAccount(peerAccount);
        dto.setLimit(limit);
        MessageAction.send(dto, Action.PULL_PRIVATE_HISTORY);
        ConsoleAction.showSimpleMsg("正在拉取与 " + peerUsername + " 的私聊历史(最多 " + limit + " 条)...");
    }

    @Override
    protected boolean check(String[] args) {
        return true;
    }

}
