package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.commons.entity.User;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.enums.Command;
import org.apache.commons.lang3.StringUtils;

/**
 * 查看与某 peer 的端到端加密安全码(Signal 风格 fingerprint)。
 *
 * <p>用法:#fingerprint {对方昵称}。</p>
 *
 * <p>必要时触发 GET_PEER_KEY 拉取对端公钥,然后用本端公钥 + 对端公钥
 * 按字典序拼接 → SHA-256 → 前 30B → 6 组 5 位十进制。</p>
 *
 * <p>若该 peer 在 {@link DataCache#peerKeyChanged} 中(本会话内检测到公钥变化),
 * 会额外输出红字"安全码已更改"提示,让用户警惕中间人攻击。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoCommand(Command.FINGERPRINT)
public class FingerprintCommandHandler extends AbstractCommandHandler {

    @Override
    public void process(String[] args) {
        if (!DataCache.isOnline) {
            ConsoleAction.showLoginMsg();
            return;
        }
        if (StringUtils.isBlank(DataCache.identityPubKey)) {
            ConsoleAction.showSimpleMsg("当前账号无 E2EE 身份(可能是游客或老账号),无法查看安全码");
            return;
        }
        if (args == null || args.length == 0) {
            ConsoleAction.showSimpleMsg(Command.FINGERPRINT.getDesc());
            return;
        }
        String peerUsername = args[0];
        User peer = DataCache.getUser(peerUsername);
        String peerAccount;
        if (peer != null && StringUtils.isNotBlank(peer.getAccount())) {
            peerAccount = peer.getAccount();
        } else {
            peerAccount = DataCache.peerAccountByUsername.get(peerUsername);
            if (StringUtils.isBlank(peerAccount)) {
                ConsoleAction.showSimpleMsg("找不到用户 " + peerUsername + " 的账号信息(对方可能未注册或未上线过)");
                return;
            }
        }

        E2EESessionService.ensureSessionKey(peerAccount).whenComplete((entry, err) -> {
            if (err != null) {
                ConsoleAction.showSimpleMsg("拉取对端公钥失败: " + err.getMessage());
                return;
            }
            String fp = E2EECrypto.computeFingerprint(DataCache.identityPubKey, entry.identityPubKey);
            StringBuilder sb = new StringBuilder();
            sb.append("====== 与 ").append(peerUsername).append(" 的安全码 ======\n");
            sb.append(fp).append("\n");
            if (DataCache.peerKeyChanged.contains(peerAccount)) {
                sb.append("⚠ 提示:对方公钥曾在本会话内变化过,可能换了设备或重置了密钥。请通过其他可信渠道核对此安全码。\n");
            } else {
                sb.append("提示:此安全码两端一致即表示通信链路未被中间人替换。\n");
            }
            sb.append("=================================");
            ConsoleAction.showSimpleMsg(sb.toString());
        });
    }

    @Override
    protected boolean check(String[] args) {
        return true;
    }

}
