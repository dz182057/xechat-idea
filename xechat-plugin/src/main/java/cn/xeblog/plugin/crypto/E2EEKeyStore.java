package cn.xeblog.plugin.crypto;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * 用 IDEA {@link PasswordSafe} 持久化 E2EE 身份私钥。
 *
 * <p>底层走 Windows DPAPI / macOS Keychain / Linux libsecret(与浏览器/Electron safeStorage
 * 同源),只有同一 OS 用户可解。这样 token 自动登录的会话也能恢复 privKey,
 * 避免每次启动都要重输密码。</p>
 *
 * <p>key 按 accountId 隔离,登出/注销时主动 {@link #clear} 即可。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
public final class E2EEKeyStore {

    private static final String SERVICE = "XEChat:e2ee-identity-priv";

    private E2EEKeyStore() {
    }

    private static CredentialAttributes attrs(long accountId) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE, String.valueOf(accountId))
        );
    }

    /** 把 32B 身份私钥用 PasswordSafe 加密落盘(以 base64url 字符串形式存) */
    public static void save(long accountId, byte[] privKey) {
        if (privKey == null || privKey.length == 0) {
            return;
        }
        try {
            String b64 = E2EECrypto.b64urlEncode(privKey);
            PasswordSafe.getInstance().setPassword(attrs(accountId), b64);
        } catch (Throwable t) {
            // PasswordSafe 失败不应阻断主流程,token 私聊降级即可
            t.printStackTrace();
        }
    }

    /** 读出落盘的私钥并解码;无记录或异常返回 null */
    public static byte[] load(long accountId) {
        try {
            String b64 = PasswordSafe.getInstance().getPassword(attrs(accountId));
            if (b64 == null || b64.isEmpty()) {
                return null;
            }
            byte[] priv = E2EECrypto.b64urlDecode(b64);
            if (priv.length != 32) {
                // 长度异常视为损坏,清掉避免下次反复加载错误数据
                clear(accountId);
                return null;
            }
            return priv;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /** 显式清除该账号的落盘私钥(登出/注销/服务端踢下线时调) */
    public static void clear(long accountId) {
        try {
            PasswordSafe.getInstance().setPassword(attrs(accountId), null);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
