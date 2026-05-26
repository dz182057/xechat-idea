package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EEKeyStore;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import cn.xeblog.plugin.ui.MainWindow;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoCommand(Command.LOGOUT)
public class LogoutCommandHandler extends AbstractCommandHandler {

    @Override
    public void process(String[] args) {
        if (!DataCache.isOnline) {
            ConsoleAction.showSimpleMsg("已是离线状态！");
            return;
        }

        if (!GameAction.isOfflineGame() && GameAction.playing()) {
            // 结束游戏
            Command.OVER.exec(args);
        }

        ConsoleAction.showSimpleMsg("正在退出中...");
        // 退出前先清粘性私聊状态:虽然 switchToLogin 也会清,这里提前清避免登出过程中的消息走错路径
        DataCache.stickyPrivateTarget = null;
        // 显式登出 → 清本机敏感凭据(token、E2EE 私钥),避免他人在同 OS 用户下复用本机自动登录
        long accountId = DataCache.accountId;
        if (accountId != 0L) {
            E2EEKeyStore.clear(accountId);
        }
        PersistenceData pd = PersistenceService.getData();
        pd.setToken(null);
        // account 字段保留,登录页下次进来还能默认填回上次的账号

        DataCache.channel.close();
        // 显式退出 → 切回登录页(channelInactive 也会兜底切,这里立即切是为了消除"主界面残留"延迟感)
        MainWindow.getInstance().switchToLogin();
    }

}
