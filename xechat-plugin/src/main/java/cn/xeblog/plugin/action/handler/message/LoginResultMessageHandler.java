package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.LoginResultDTO;
import cn.xeblog.commons.entity.PullHistoryDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EEKeyStore;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import cn.xeblog.plugin.ui.MainWindow;
import org.apache.commons.lang3.StringUtils;

/**
 * 登录结果消息(LOGIN_RESULT)。
 *
 * <p>账号登录/token 登录/游客登录成功后,服务端推送 LoginResultDTO,客户端:</p>
 * <ul>
 *   <li>账号/token 登录:持久化 token+account 到 PersistenceData,后续启动可自动登录</li>
 *   <li>游客登录:不持久化任何凭据,只同步 DataCache.username 为服务端给的 nickname</li>
 *   <li>统一:把 DataCache.username 同步为服务端 nickname,保证 ONLINE_USERS 自查命中</li>
 * </ul>
 *
 * @author dz
 * @date 2026/5/25
 */
@DoMessage(MessageType.LOGIN_RESULT)
public class LoginResultMessageHandler extends AbstractMessageHandler<LoginResultDTO> {

    @Override
    protected void process(Response<LoginResultDTO> response) {
        LoginResultDTO body = response.getBody();
        if (body == null) {
            return;
        }

        // 立即切到 MAIN 卡 — UI 不被后续 E2EE 派生(Argon2id ~0.5-1.5s)等步骤阻塞,
        // 同时确保中间任何步骤抛异常也不会让用户卡在登录页 loading
        MainWindow.getInstance().switchToMain();
        MainWindow.getInstance().getLoginPanel().onLoginSucceeded();

        try {
            User user = body.getUser();
            if (user != null) {
                // 把 DataCache.username 同步为服务端 nickname(注册账号或游客都做),
                // 保证 ONLINE_USERS 列表里的 user.username == DataCache.username
                DataCache.username = user.getNickname();
                DataCache.accountId = user.getAccountId();
            }

            // 账号体系登录:持久化 token + account(IDEA PersistentStateComponent 关闭时自动落盘)
            if (StringUtils.isNotBlank(body.getToken()) && user != null && !user.isGuest()) {
                PersistenceData pd = PersistenceService.getData();
                pd.setToken(body.getToken());
                pd.setAccount(DataCache.account);
                ConsoleAction.showSimpleMsg("登录成功: " + user.getNickname() + " (账号 " + DataCache.account + ")");
            } else if (user != null && user.isGuest()) {
                ConsoleAction.showSimpleMsg("游客模式已就绪: " + user.getNickname() + " (仅大厅聊天,不能私聊)");
            }

            // E2EE: 派生 master key + 解包身份私钥(仅"账号+密码"登录路径可解,token 路径密钥不可用)
            unlockE2EE(body);

            // 登录成功后拉公共频道近 3 天历史(plugin 不持久化文件缓存,IDE 关闭即丢,
            // 重新登录时再拉一次即可,符合 IDEA 插件的会话式特性)
            PullHistoryDTO pull = new PullHistoryDTO();
            pull.setSinceMs(System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000);
            pull.setLimit(50);
            MessageAction.send(pull, Action.PULL_HISTORY);
        } catch (Throwable t) {
            // 任何意外错误都进控制台,UI 已切到 MAIN 卡,用户至少能看到诊断信息
            t.printStackTrace();
            ConsoleAction.showSimpleMsg("登录后置流程异常: " + t.getMessage());
        }
    }

    /**
     * 解锁 E2EE 身份私钥并缓存到 {@link DataCache#identityPrivKey}。
     *
     * <p>三条路径:</p>
     * <ul>
     *   <li>密码登录:用 DataCache.password 派生 master → 解 envelope → 写 DataCache,
     *       同时把 raw privKey 用 IDEA PasswordSafe 加密落盘(下次 token 登录可恢复)</li>
     *   <li>token 自动登录(无密码):先尝试 PasswordSafe.load,命中即恢复 privKey,
     *       与桌面端 safeStorage 路径等价</li>
     *   <li>PasswordSafe 也读不到 → 提示用户用密码重登一次,完成首次落盘</li>
     * </ul>
     *
     * <p>派生耗时较长(Argon2id 64MB×3,本机约 0.5-1.5s),放在 Netty 线程里不影响 UI,
     * 但会阻塞后续消息派发。考虑到登录后第一条消息往往是 PULL_HISTORY 异步响应,
     * 这点延迟可接受。</p>
     */
    private void unlockE2EE(LoginResultDTO body) {
        // 派生完(或决定无法派生)后,统一清掉 DataCache.password
        String password = DataCache.password;
        try {
            User user = body.getUser();
            if (user == null || user.isGuest()) {
                return; // 游客无密钥
            }
            DataCache.identityPubKey = body.getIdentityPubKey();
            if (StringUtils.isBlank(body.getE2eeSalt()) || StringUtils.isBlank(body.getIdentityPrivKeyEnvelope())) {
                return; // 服务端没下发(老版本或异常),静默退化
            }

            long accountId = user.getAccountId();

            if (StringUtils.isNotBlank(password)) {
                // 密码登录:派生 master → 解 envelope → 写内存 + PasswordSafe 落盘
                byte[] masterKey = E2EECrypto.deriveMasterKey(password, body.getE2eeSalt());
                byte[] priv;
                try {
                    priv = E2EECrypto.openWithMaster(masterKey, body.getIdentityPrivKeyEnvelope());
                } finally {
                    // master 用过即清,只保留 identityPrivKey
                    java.util.Arrays.fill(masterKey, (byte) 0);
                }
                DataCache.identityPrivKey = priv;
                E2EEKeyStore.save(accountId, priv);
                return;
            }

            // token 自动登录路径:无密码,尝试从 PasswordSafe 恢复 privKey
            byte[] restored = E2EEKeyStore.load(accountId);
            if (restored != null) {
                DataCache.identityPrivKey = restored;
                return;
            }

            // 历史用户(从未在本机做过密码登录) → 落盘记录不存在,提示用密码重登一次
            ConsoleAction.showSimpleMsg(
                    "私聊密钥未解锁(本机首次自动登录),请 #logout 后用账号密码重新登录一次完成本机密钥初始化");
        } catch (Exception e) {
            ConsoleAction.showSimpleMsg("E2EE 密钥解锁失败: " + e.getMessage());
        } finally {
            DataCache.password = null;
        }
    }

}
