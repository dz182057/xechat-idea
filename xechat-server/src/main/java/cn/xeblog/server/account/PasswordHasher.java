package cn.xeblog.server.account;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Argon2id 密码哈希封装。
 *
 * <p>参数: m=64MB, t=3, p=1, hashLen=32, saltLen=16(设计文档约定)。
 * 单例使用,Argon2Factory 内部线程安全。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
public final class PasswordHasher {

    /**
     * 内存代价(KB),64 MB
     */
    private static final int MEMORY_KB = 65_536;

    /**
     * 迭代次数
     */
    private static final int ITERATIONS = 3;

    /**
     * 并行度
     */
    private static final int PARALLELISM = 1;

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private static final Argon2 ARGON2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id, SALT_LENGTH, HASH_LENGTH);

    private PasswordHasher() {
    }

    /**
     * 对明文密码做 Argon2id 哈希。
     *
     * @param rawPassword 明文密码
     * @return Argon2 编码字符串(含算法/参数/salt/hash)
     */
    public static String hash(String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, chars);
        } finally {
            ARGON2.wipeArray(chars);
        }
    }

    /**
     * 校验密码是否匹配。
     *
     * @param encoded     入库的 Argon2 编码字符串
     * @param rawPassword 用户提交的明文密码
     */
    public static boolean verify(String encoded, String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return ARGON2.verify(encoded, chars);
        } finally {
            ARGON2.wipeArray(chars);
        }
    }

    /**
     * 密码策略校验: 至少 8 位,且同时包含字母和数字。
     *
     * @throws IllegalArgumentException 不符合策略时抛出,message 含中文错误描述
     */
    public static void validatePolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("密码至少 8 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return;
            }
        }
        throw new IllegalArgumentException("密码须同时包含字母和数字");
    }

}
