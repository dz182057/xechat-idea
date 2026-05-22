package cn.xeblog.server.account;

import cn.xeblog.commons.entity.InviteCodeDTO;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.entity.InviteCode;
import cn.xeblog.server.account.mapper.AccountMapper;
import cn.xeblog.server.account.mapper.InviteCodeMapper;
import cn.xeblog.server.account.mapper.SystemStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 邀请码服务。
 *
 * <p>核心入口:</p>
 * <ul>
 *     <li>{@link #generateInitialSetupTokenIfNeeded()}:启动时探测,首启自动生成 setup-token</li>
 *     <li>{@link #generate(Long, int, Integer, String)}:管理员创建邀请码</li>
 *     <li>{@link #consume(String, long)}:注册时消费邀请码,返回是否为初始管理员</li>
 *     <li>{@link #list(boolean)}:管理员邀请码列表</li>
 *     <li>{@link #revoke(String)}:管理员吊销邀请码</li>
 * </ul>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
public final class InviteCodeService {

    /**
     * 普通邀请码字符集
     */
    private static final char[] CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /**
     * 普通邀请码长度
     */
    private static final int CODE_LENGTH = 12;

    /**
     * system_state 中的"首启待领"标记 key
     */
    public static final String KEY_PENDING_INITIAL_ADMIN = "pending_initial_admin";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private InviteCodeService() {
    }

    /**
     * 启动时检测:若 accounts 表为空,生成 setup-token 写入 invite_codes,
     * 同时把 system_state.pending_initial_admin 置为 true,首注册者升 ADMIN。
     */
    public static void generateInitialSetupTokenIfNeeded() {
        if (AccountService.countAll() > 0) {
            return;
        }

        // 32 字节 SecureRandom → base64url
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String setupToken = URL_ENCODER.encodeToString(buf);

        long now = System.currentTimeMillis();
        InviteCode invite = InviteCode.builder()
                .code(setupToken)
                .createdBy(null)
                .createdAt(now)
                .expiresAt(null)
                .maxUses(1)
                .usedCount(0)
                .revoked(false)
                .note("初始管理员引导")
                .build();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(InviteCodeMapper.class).insert(invite);
            session.getMapper(SystemStateMapper.class).set(KEY_PENDING_INITIAL_ADMIN, "true");
        }

        // 醒目打印
        String banner = "======================================================================";
        log.info(banner);
        log.info("|  首次启动:已生成初始管理员引导邀请码(setup-token)             |");
        log.info("|  使用此邀请码注册的第一个账号将自动获得 ADMIN 角色               |");
        log.info("|                                                                    |");
        log.info("|  setup-token = {}", setupToken);
        log.info(banner);
    }

    /**
     * 管理员创建邀请码。
     *
     * @param createdBy     生成者 accountId
     * @param maxUses       最大使用次数,默认 1,0 表示无限
     * @param expiresInDays null 表示永久;否则 now + n*86400_000ms
     * @return 新邀请码字符串
     */
    public static String generate(Long createdBy, int maxUses, Integer expiresInDays, String note) {
        long now = System.currentTimeMillis();
        Long expiresAt = expiresInDays == null ? null
                : now + expiresInDays.longValue() * 24 * 60 * 60 * 1000;

        String code = generateUniqueCode();
        InviteCode invite = InviteCode.builder()
                .code(code)
                .createdBy(createdBy)
                .createdAt(now)
                .expiresAt(expiresAt)
                .maxUses(maxUses)
                .usedCount(0)
                .revoked(false)
                .note(note)
                .build();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(InviteCodeMapper.class).insert(invite);
        }
        return code;
    }

    private static String generateUniqueCode() {
        for (int tries = 0; tries < 10; tries++) {
            char[] buf = new char[CODE_LENGTH];
            for (int i = 0; i < CODE_LENGTH; i++) {
                buf[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
            }
            String code = new String(buf);
            try (SqlSession session = DbInitializer.factory().openSession(true)) {
                if (session.getMapper(InviteCodeMapper.class).findByCode(code) == null) {
                    return code;
                }
            }
        }
        throw new AccountException("生成邀请码失败,请重试");
    }

    /**
     * 消费邀请码(注册流程内调用)。
     *
     * <p>校验顺序:存在 → 未吊销 → 未过期 → 未用满 → 自增 used_count + 回填 used_by/used_at。
     * 若 system_state.pending_initial_admin=true,使用 setup-token 消费时返回 true,
     * 同时清除该标记。</p>
     *
     * @return true=首注册者应升 ADMIN
     * @throws AccountException 邀请码无效/已吊销/已过期/已用满
     */
    public static boolean consume(String code, long accountId) {
        if (code == null || code.isEmpty()) {
            throw new AccountException("邀请码不能为空");
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            InviteCodeMapper inviteMapper = session.getMapper(InviteCodeMapper.class);
            SystemStateMapper sysMapper = session.getMapper(SystemStateMapper.class);

            InviteCode invite = inviteMapper.findByCode(code);
            if (invite == null) {
                throw new AccountException("邀请码无效");
            }
            if (invite.isRevoked()) {
                throw new AccountException("邀请码已被吊销");
            }
            if (invite.getExpiresAt() != null && invite.getExpiresAt() < now) {
                throw new AccountException("邀请码已过期");
            }
            if (invite.getMaxUses() != 0 && invite.getUsedCount() >= invite.getMaxUses()) {
                throw new AccountException("邀请码已用满");
            }

            inviteMapper.incrementUsed(code, accountId, now);

            // 检查是否触发"首注册者升 ADMIN"
            boolean isInitialAdmin = "true".equals(sysMapper.get(KEY_PENDING_INITIAL_ADMIN));
            if (isInitialAdmin) {
                sysMapper.delete(KEY_PENDING_INITIAL_ADMIN);
                log.info("首注册者将升 ADMIN(消费了 setup-token)");
            }
            return isInitialAdmin;
        }
    }

    /**
     * 邀请码列表(管理员视图)。
     *
     * @param includeUsed 是否包含已用满/已吊销/已过期
     */
    public static List<InviteCodeDTO> list(boolean includeUsed) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<InviteCode> rows = session.getMapper(InviteCodeMapper.class).listAll(includeUsed);
            if (rows.isEmpty()) {
                return new ArrayList<>();
            }

            // 一次性把 created_by 的昵称查回来,避免 N+1
            Set<Long> creatorIds = new HashSet<>();
            for (InviteCode r : rows) {
                if (r.getCreatedBy() != null) {
                    creatorIds.add(r.getCreatedBy());
                }
            }
            Map<Long, String> creatorNicknames = new HashMap<>();
            if (!creatorIds.isEmpty()) {
                List<Account> creators = session.getMapper(AccountMapper.class).findByIdIn(creatorIds);
                for (Account a : creators) {
                    creatorNicknames.put(a.getAccountId(), a.getNickname());
                }
            }

            List<InviteCodeDTO> result = new ArrayList<>(rows.size());
            for (InviteCode r : rows) {
                InviteCodeDTO dto = new InviteCodeDTO();
                dto.setCode(r.getCode());
                dto.setCreatedBy(r.getCreatedBy());
                dto.setCreatedByNickname(
                        r.getCreatedBy() == null ? null : creatorNicknames.get(r.getCreatedBy()));
                dto.setCreatedAt(r.getCreatedAt());
                dto.setExpiresAt(r.getExpiresAt());
                dto.setMaxUses(r.getMaxUses());
                dto.setUsedCount(r.getUsedCount());
                dto.setRevoked(r.isRevoked());
                dto.setNote(r.getNote());
                result.add(dto);
            }
            return result;
        }
    }

    public static void revoke(String code) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            int n = session.getMapper(InviteCodeMapper.class).revoke(code);
            if (n == 0) {
                throw new AccountException("邀请码不存在");
            }
        }
    }

}
