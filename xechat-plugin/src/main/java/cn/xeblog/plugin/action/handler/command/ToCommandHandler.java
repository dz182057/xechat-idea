package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.commons.entity.User;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.InputAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.enums.Command;
import org.apache.commons.lang3.StringUtils;

/**
 * #to {昵称} 进入粘性私聊;#to 无参解除。
 *
 * <p>设置 {@link DataCache#stickyPrivateTarget} 并刷新输入区 banner。
 * 真正的发送路径仍在 {@link InputAction#sendMsg},此命令只管"锁定/解除"状态。</p>
 *
 * <p>校验:在线 / 非游客 / peer 存在 / peer 非游客 / peer 非自己 / 有 E2EE 私钥。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoCommand(Command.TO)
public class ToCommandHandler extends AbstractCommandHandler {

    @Override
    public void process(String[] args) {
        // 无参 → 解除
        if (args == null || args.length == 0 || StringUtils.isBlank(args[0])) {
            if (DataCache.stickyPrivateTarget == null) {
                ConsoleAction.showSimpleMsg("当前不在私聊模式");
                return;
            }
            String oldTarget = DataCache.stickyPrivateTarget;
            DataCache.stickyPrivateTarget = null;
            InputAction.hidePrivateBanner();
            ConsoleAction.showSimpleMsg("已退出与 @" + oldTarget + " 的私聊模式");
            return;
        }

        String peerUsername = args[0];

        // 不能私聊自己
        if (peerUsername.equals(DataCache.username)) {
            ConsoleAction.showSimpleMsg("不能私聊自己");
            return;
        }

        // 游客模式禁止私聊
        if (DataCache.guestMode) {
            ConsoleAction.showSimpleMsg("游客模式不能私聊,请先用 #login {账号} {密码} 登录账号");
            return;
        }

        // E2EE 私钥必须可用(token 自动登录路径下无私钥)
        if (DataCache.identityPrivKey == null) {
            ConsoleAction.showSimpleMsg("E2EE 私钥未解锁,token 登录无法私聊,请 #exit 后用密码重登");
            return;
        }

        User peer = DataCache.getUser(peerUsername);
        if (peer == null) {
            ConsoleAction.showSimpleMsg("用户 " + peerUsername + " 不在线");
            return;
        }

        if (StringUtils.isBlank(peer.getAccount())) {
            ConsoleAction.showSimpleMsg(peerUsername + " 是游客,不能私聊");
            return;
        }

        DataCache.stickyPrivateTarget = peerUsername;
        InputAction.showPrivateBanner(peerUsername);
        ConsoleAction.showSimpleMsg("已锁定私聊对象 @" + peerUsername + ",输入消息将直接发给对方;再次 #to 解除");
    }

    @Override
    protected boolean check(String[] args) {
        if (!DataCache.isOnline) {
            ConsoleAction.showLoginMsg();
            return false;
        }
        return true;
    }

}
