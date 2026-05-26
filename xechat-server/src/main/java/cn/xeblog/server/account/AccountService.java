package cn.xeblog.server.account;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.mapper.AccountMapper;
import cn.xeblog.server.account.mapper.SessionMapper;
import cn.xeblog.server.e2ee.entity.KeyEnvelope;
import cn.xeblog.server.e2ee.mapper.KeyEnvelopeMapper;
import cn.xeblog.server.util.SensitiveWordUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 账号核心服务:注册/登录校验/改密/改昵称/软删等。
 *
 * <p>本类只关心 accounts 表的读写 + 密码哈希;邀请码消耗和 token 创建由
 * {@link InviteCodeService}/{@link SessionService} 各自负责,Handler 层做编排。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
public final class AccountService {

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    /**
     * 昵称长度上限(字符数)
     */
    private static final int NICKNAME_MAX_LEN = 12;

    private AccountService() {
    }

    // ============ 校验 ============

    public static void validateAccountFormat(String account) {
        if (StrUtil.isBlank(account) || !ACCOUNT_PATTERN.matcher(account).matches()) {
            throw new AccountException("账号格式不合法(4-20 位字母数字下划线)");
        }
    }

    public static void validateNicknameFormat(String nickname) {
        if (StrUtil.isBlank(nickname)) {
            throw new AccountException("昵称不能为空");
        }
        if (nickname.length() > NICKNAME_MAX_LEN) {
            throw new AccountException("昵称最多 " + NICKNAME_MAX_LEN + " 个字符");
        }
        if (SensitiveWordUtils.hasSensitiveWord(nickname)) {
            throw new AccountException("昵称含违规字符");
        }
    }

    // ============ 注册 ============

    /**
     * 注册新账号(假设邀请码已校验消费完成)。
     *
     * <p>E2EE 三件套(e2eeSalt+identityPubKey+identityPrivKeyEnvelope)是一组,要么都传,要么都 null
     * (老客户端兜底)。若三者齐全,accounts 写两列,同事务 upsert key_envelopes 一行(type=IDENTITY)。</p>
     *
     * @param role                    {@link Account#ROLE_ADMIN} / {@link Account#ROLE_USER}
     * @param e2eeSalt                客户端派生 masterKey 的 salt(base64url 16B),老客户端为 null
     * @param identityPubKey          X25519 身份公钥(base64url 32B),老客户端为 null
     * @param identityPrivKeyEnvelope masterKey 包裹后的身份私钥(base64url iv||ct),老客户端为 null
     * @return 新建账号
     */
    public static Account register(String account, String rawPassword, String nickname,
                                   String role, String ip,
                                   String e2eeSalt, String identityPubKey,
                                   String identityPrivKeyEnvelope) {
        validateAccountFormat(account);
        validateNicknameFormat(nickname);
        PasswordHasher.validatePolicy(rawPassword);
        validateE2eeBundle(e2eeSalt, identityPubKey, identityPrivKeyEnvelope);

        // accounts + key_envelopes 必须同事务,关掉 autoCommit
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);

            if (mapper.findByAccount(account) != null) {
                throw new AccountException("账号已被注册");
            }
            if (mapper.findByNickname(nickname) != null) {
                throw new AccountException("昵称已被占用");
            }

            long accountId = IdUtil.getSnowflakeNextId();
            String hash = PasswordHasher.hash(rawPassword);
            long now = System.currentTimeMillis();

            Account newOne = Account.builder()
                    .accountId(accountId)
                    .account(account)
                    .nickname(nickname)
                    .passwordHash(hash)
                    .avatarVersion(0)
                    .role(role == null ? Account.ROLE_USER : role)
                    .permit(0)
                    .status(Account.STATUS_ACTIVE)
                    .createdAt(now)
                    .createdIp(ip)
                    .e2eeSalt(e2eeSalt)
                    .identityPubKey(identityPubKey)
                    .build();
            mapper.insert(newOne);

            if (StrUtil.isNotBlank(identityPrivKeyEnvelope)) {
                KeyEnvelope envelope = KeyEnvelope.builder()
                        .accountId(accountId)
                        .type(KeyEnvelope.TYPE_IDENTITY)
                        .envelope(identityPrivKeyEnvelope)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                session.getMapper(KeyEnvelopeMapper.class).upsert(envelope);
            }

            session.commit();
            log.info("账号注册成功 accountId={} account={} nickname={} role={} e2ee={}",
                    accountId, account, nickname, role,
                    StrUtil.isNotBlank(identityPubKey));
            return newOne;
        }
    }

    /**
     * E2EE 三件套要么全有要么全无,避免 accounts/key_envelopes 半边初始化的脏数据。
     */
    private static void validateE2eeBundle(String salt, String pubKey, String envelope) {
        boolean hasSalt = StrUtil.isNotBlank(salt);
        boolean hasPubKey = StrUtil.isNotBlank(pubKey);
        boolean hasEnvelope = StrUtil.isNotBlank(envelope);
        if (hasSalt == hasPubKey && hasPubKey == hasEnvelope) {
            return;
        }
        throw new AccountException("E2EE 字段不完整(e2eeSalt/identityPubKey/identityPrivKeyEnvelope 要么都传要么都不传)");
    }

    // ============ 登录校验 ============

    /**
     * 用账号+密码登录。
     *
     * @return 登录成功后的 Account(已带最新 last_login_*)
     * @throws AccountException 账号不存在/密码错/被冻结/已注销
     */
    public static Account login(String account, String rawPassword, String ip) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(rawPassword)) {
            throw new AccountException("账号或密码不能为空");
        }

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);
            Account a = mapper.findByAccount(account);
            if (a == null) {
                throw new AccountException("账号不存在");
            }
            checkStatusUsable(a);
            if (!PasswordHasher.verify(a.getPasswordHash(), rawPassword)) {
                throw new AccountException("密码错误");
            }

            long now = System.currentTimeMillis();
            mapper.updateLastLogin(a.getAccountId(), now, ip);
            a.setLastLoginAt(now);
            a.setLastLoginIp(ip);
            return a;
        }
    }

    public static Account findById(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(AccountMapper.class).findById(accountId);
        }
    }

    public static Account findByAccount(String account) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(AccountMapper.class).findByAccount(account);
        }
    }

    /**
     * 提升账号角色(仅用于首注册者升 ADMIN)
     */
    public static void updateRole(long accountId, String role) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(AccountMapper.class).updateRole(accountId, role);
        }
    }

    public static long countAll() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(AccountMapper.class).countAll();
        }
    }

    // ============ 改密码 ============

    public static void changePassword(long accountId, String oldPwd, String newPwd) {
        PasswordHasher.validatePolicy(newPwd);

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);
            Account a = mapper.findById(accountId);
            if (a == null) {
                throw new AccountException("账号不存在");
            }
            checkStatusUsable(a);
            if (!PasswordHasher.verify(a.getPasswordHash(), oldPwd)) {
                throw new AccountException("原密码错误");
            }
            mapper.updatePassword(accountId, PasswordHasher.hash(newPwd));
            // 改密后吊销该账号全部 token,强制重新登录
            session.getMapper(SessionMapper.class).revokeAllByAccount(accountId);
            log.info("账号 {} 修改密码,已吊销其全部 token", accountId);
        }
    }

    // ============ 改昵称 ============

    /**
     * 修改昵称(唯一性 + 合法性 + 敏感词校验)。
     *
     * @return 改后的 Account
     */
    public static Account changeNickname(long accountId, String newNickname) {
        validateNicknameFormat(newNickname);

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);
            Account a = mapper.findById(accountId);
            if (a == null) {
                throw new AccountException("账号不存在");
            }
            checkStatusUsable(a);
            if (newNickname.equals(a.getNickname())) {
                return a; // 与现昵称相同,什么都不用做
            }
            if (mapper.findByNickname(newNickname) != null) {
                throw new AccountException("昵称已被占用");
            }
            mapper.updateNickname(accountId, newNickname);
            a.setNickname(newNickname);
            return a;
        }
    }

    // ============ 改头像版本 ============

    /**
     * AvatarService 写完文件后调用,把 avatar_version 自增并返回新值。
     */
    public static int incrementAvatarVersion(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);
            mapper.incrementAvatarVersion(accountId);
            Account a = mapper.findById(accountId);
            return a == null ? 0 : a.getAvatarVersion();
        }
    }

    // ============ 软删 ============

    /**
     * 软删账号: status=DELETED + deleted_at + 删头像文件 + 吊销全部 session。
     */
    public static void softDelete(long accountId) {
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(AccountMapper.class).softDelete(accountId, now);
            session.getMapper(SessionMapper.class).revokeAllByAccount(accountId);
        }
        // 删头像文件(失败不影响数据库已经软删的事实)
        try {
            Path avatar = Paths.get(cn.xeblog.server.config.GlobalConfig.AVATAR_DIR, accountId + ".png");
            Files.deleteIfExists(avatar);
        } catch (Exception e) {
            log.warn("软删账号 {} 时删头像文件失败: {}", accountId, e.getMessage());
        }
        log.info("账号 {} 已软删,所有 token 吊销", accountId);
    }

    // ============ 内部 ============

    private static void checkStatusUsable(Account a) {
        if (Account.STATUS_DELETED.equals(a.getStatus())) {
            throw new AccountException("账号已注销");
        }
        if (Account.STATUS_FROZEN.equals(a.getStatus())) {
            throw new AccountException("账号已被冻结");
        }
    }

}
