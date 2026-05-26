package cn.xeblog.server.e2ee;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.e2ee.entity.KeyEnvelope;
import cn.xeblog.server.e2ee.mapper.KeyEnvelopeMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

/**
 * E2EE 密钥服务: key_envelopes 表的独立事务查询 + peer 公钥查询。
 *
 * <p>注册流程的 envelope 写入必须与 accounts 同事务,所以走
 * {@link AccountService#register(String, String, String, String, String, String, String, String)} 内部
 * 的同 SqlSession 直接调 KeyEnvelopeMapper,不走这里。本 service 服务"独立查询"路径:
 * 登录时回读身份信封、GET_PEER_KEY 时查对端公钥。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Slf4j
public final class E2EEKeyService {

    private E2EEKeyService() {
    }

    /**
     * 查身份私钥信封(base64url iv||ciphertext);老账号(注册时无 E2EE)返回 null。
     */
    public static String findIdentityEnvelope(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            KeyEnvelope e = session.getMapper(KeyEnvelopeMapper.class)
                    .find(accountId, KeyEnvelope.TYPE_IDENTITY);
            return e == null ? null : e.getEnvelope();
        }
    }

    /**
     * 查 peer 公钥(供 GET_PEER_KEY)。
     * 抛错: peer 不存在 / 已注销 / 没有公钥(老账号或游客)。
     */
    public static Account requirePeerWithPubKey(String account) {
        if (StrUtil.isBlank(account)) {
            throw new AccountException("对端账号不能为空");
        }
        Account a = AccountService.findByAccount(account);
        if (a == null) {
            throw new AccountException("对端账号不存在");
        }
        if (Account.STATUS_DELETED.equals(a.getStatus())) {
            throw new AccountException("对端账号已注销");
        }
        if (StrUtil.isBlank(a.getIdentityPubKey())) {
            throw new AccountException("对端未启用 E2EE(版本过低)");
        }
        return a;
    }

}
