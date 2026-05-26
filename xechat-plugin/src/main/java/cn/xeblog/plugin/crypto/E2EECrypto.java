package cn.xeblog.plugin.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * E2EE 私聊加密原语层(plugin 端)。
 *
 * <p>严格对齐 docs/design/e2ee-and-history.md §八 与 desktop 端
 * {@code src/renderer/src/lib/crypto.ts},参数 / 编码 / 串拼接顺序必须字节级一致,
 * 任何细节偏差都会导致两端互不解密。两端正确性由 .verify/ 双向回环对拍(F 段)兜底。</p>
 *
 * <ul>
 *   <li>Argon2id   m=64MB(65536KB), t=3, p=1, saltLen=16, hashLen=32(argon2-jvm 同 server)</li>
 *   <li>X25519     raw 32B 公私钥(JDK 21 XECKey;公钥 u 坐标按 RFC 7748 little-endian 编码)</li>
 *   <li>HKDF-SHA256 input=ECDH 共享密钥; salt=空(实际按 RFC 5869 替换为 32B 零);
 *                  info="xechat-session-v1|" + min(idA,idB) + "|" + max(...);output=32B</li>
 *   <li>AES-256-GCM IV 12B 随机, GCM tag 128bit, ciphertext 与 tag 拼接(JDK 默认与 WebCrypto 一致)</li>
 *   <li>身份私钥信封 = base64url(iv || ciphertext) 单字段</li>
 *   <li>私聊消息信封 = iv / ciphertext 分两个 base64url 字段(EncryptedEnvelopeDTO)</li>
 *   <li>Fingerprint = SHA-256(min(pubA,pubB) || max(pubA,pubB)) 前 30B → 6 组 5 位十进制</li>
 * </ul>
 *
 * <p>本类纯函数式,无任何 IDEA / Swing 依赖,可被普通 JUnit 测试覆盖。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
public final class E2EECrypto {

    // ============ 协议参数(与 desktop crypto.ts 字面一致) ============

    /** Argon2id 内存代价 KB */
    private static final int ARGON2_MEMORY_KB = 65_536;
    /** Argon2id 迭代次数 */
    private static final int ARGON2_ITERATIONS = 3;
    /** Argon2id 并行度 */
    private static final int ARGON2_PARALLELISM = 1;
    /** master key 长度(AES-256) */
    private static final int MASTER_KEY_LEN = 32;
    /** e2eeSalt 长度 */
    private static final int E2EE_SALT_LEN = 16;
    /** AES-GCM IV 长度 */
    private static final int AES_GCM_IV_LEN = 12;
    /** AES-GCM tag 长度(bit) */
    private static final int AES_GCM_TAG_BITS = 128;
    /** 会话密钥长度 */
    private static final int SESSION_KEY_LEN = 32;
    /** X25519 公私钥长度 */
    private static final int X25519_KEY_LEN = 32;
    /** HKDF info 前缀 */
    private static final String HKDF_INFO_PREFIX = "xechat-session-v1|";
    /** Fingerprint 取 SHA-256 前 30B → 6 组 5 位 */
    private static final int FINGERPRINT_BYTES = 30;
    private static final int FINGERPRINT_GROUPS = 6;
    private static final int FINGERPRINT_GROUP_LEN = 5;

    private static final Base64.Encoder B64URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private E2EECrypto() {
    }

    // ============ base64url ============

    public static String b64urlEncode(byte[] bytes) {
        return B64URL_ENCODER.encodeToString(bytes);
    }

    /** 兼容带与不带 padding 的输入(Base64.getUrlDecoder 默认就支持) */
    public static byte[] b64urlDecode(String s) {
        return B64URL_DECODER.decode(s);
    }

    // ============ 随机 ============

    public static byte[] randomBytes(int len) {
        byte[] out = new byte[len];
        RANDOM.nextBytes(out);
        return out;
    }

    /** 生成 16B 的 e2eeSalt(base64url,注册时一次性,与登录密码 hash salt 完全分离) */
    public static String generateE2eeSalt() {
        return b64urlEncode(randomBytes(E2EE_SALT_LEN));
    }

    // ============ Argon2id: 密码 + salt → master key ============

    /**
     * 由密码与 e2eeSalt 派生 master key(32B raw)。
     *
     * <p>用 BouncyCastle 的 {@link Argon2BytesGenerator}(纯 Java,无 JNA / native lib),
     * 参数与桌面端 hash-wasm 默认值 + argon2-jvm 完全一致:argon2id v1.3 + m=64MB + t=3 +
     * p=1 + hashLen=32,UTF-8 编码密码。三端字节级一致由 .verify/ 双向回环对拍兜底。</p>
     */
    public static byte[] deriveMasterKey(String password, String saltB64url) {
        byte[] salt = b64urlDecode(saltB64url);
        if (salt.length != E2EE_SALT_LEN) {
            throw new IllegalArgumentException(
                    "e2eeSalt 长度必须 " + E2EE_SALT_LEN + "B,实际 " + salt.length + "B");
        }
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ARGON2_ITERATIONS)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withParallelism(ARGON2_PARALLELISM)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[MASTER_KEY_LEN];
        char[] chars = password.toCharArray();
        try {
            // generateBytes(char[]) 内部按 UTF-8 编码密码,与 hash-wasm / argon2-jvm 默认行为一致
            generator.generateBytes(chars, out);
            return out;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    // ============ X25519 身份密钥对 ============

    public static final class IdentityKeyPair {
        /** raw 32B 私钥 */
        public final byte[] privKey;
        /** raw 32B 公钥的 base64url(可直接用作 RegisterDTO.identityPubKey) */
        public final String pubKeyB64url;

        public IdentityKeyPair(byte[] privKey, String pubKeyB64url) {
            this.privKey = privKey;
            this.pubKeyB64url = pubKeyB64url;
        }
    }

    /**
     * 生成 X25519 身份密钥对。私钥应只在内存留存,master 包裹后才落服务端。
     */
    public static IdentityKeyPair generateIdentityKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair kp = kpg.generateKeyPair();
            byte[] raw = extractRawPrivateKey((XECPrivateKey) kp.getPrivate());
            byte[] pub = extractRawPublicKey((XECPublicKey) kp.getPublic());
            return new IdentityKeyPair(raw, b64urlEncode(pub));
        } catch (Exception e) {
            throw new IllegalStateException("X25519 密钥对生成失败", e);
        }
    }

    /**
     * 由 raw 私钥反推对应公钥(测试 / 本地校验用)。
     * JDK 没有暴露纯标量乘法 API,这里用本文件底部 RFC 7748 §5 实现的
     * {@link X25519#scalarMultBase}。生产路径下,登录后用 generate 时
     * 一并拿到 pubKey 即可,不必走此方法。
     */
    public static String publicKeyFromPrivate(byte[] rawPrivKey) {
        if (rawPrivKey.length != X25519_KEY_LEN) {
            throw new IllegalArgumentException("X25519 私钥必须 32B");
        }
        return b64urlEncode(X25519.scalarMultBase(rawPrivKey));
    }

    /**
     * 从 JDK XECPrivateKey 提取 raw 32B 标量(scalar)。
     * JDK 的 XECPrivateKey.getScalar() 返回的就是 raw little-endian scalar 字节。
     */
    private static byte[] extractRawPrivateKey(XECPrivateKey priv) {
        Optional<byte[]> opt = priv.getScalar();
        if (opt.isEmpty()) {
            throw new IllegalStateException("无法从 XECPrivateKey 提取 raw scalar");
        }
        byte[] raw = opt.get();
        if (raw.length != X25519_KEY_LEN) {
            throw new IllegalStateException(
                    "X25519 私钥 raw 长度异常: " + raw.length);
        }
        return raw;
    }

    /**
     * 从 JDK XECPublicKey 提取 raw 32B u 坐标(RFC 7748 little-endian)。
     * XECPublicKey.getU() 返回 BigInteger 数值,需要按 little-endian 重新编码。
     */
    private static byte[] extractRawPublicKey(XECPublicKey pub) {
        BigInteger u = pub.getU();
        return bigIntToLittleEndian(u, X25519_KEY_LEN);
    }

    private static byte[] bigIntToLittleEndian(BigInteger u, int outLen) {
        byte[] be = u.toByteArray(); // big-endian, 可能含 sign byte
        byte[] le = new byte[outLen];
        // 复制时去掉 leading sign byte,然后倒序
        int start = (be.length > outLen && be[0] == 0) ? 1 : 0;
        int len = Math.min(be.length - start, outLen);
        for (int i = 0; i < len; i++) {
            le[i] = be[(start + len - 1) - i];
        }
        return le;
    }

    private static BigInteger littleEndianToBigInt(byte[] le) {
        // BigInteger 期望 big-endian + 正数(前缀 0)
        byte[] be = new byte[le.length + 1];
        be[0] = 0;
        for (int i = 0; i < le.length; i++) {
            be[1 + i] = le[le.length - 1 - i];
        }
        return new BigInteger(be);
    }

    /**
     * 把 raw X25519 公私钥实例化为 JDK PublicKey / PrivateKey 供 KeyAgreement 使用。
     */
    private static PrivateKey rawToPrivateKey(byte[] rawPriv) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("X25519");
        return kf.generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, rawPriv));
    }

    private static PublicKey rawToPublicKey(byte[] rawPub) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("X25519");
        BigInteger u = littleEndianToBigInt(rawPub);
        return kf.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, u));
    }

    // ============ AES-256-GCM 包裹 / 解包(身份私钥信封) ============

    /**
     * 用 master key 包裹任意明文,产出 base64url(iv || ciphertext)。
     */
    public static String sealWithMaster(byte[] masterKey, byte[] plaintext) {
        byte[] iv = randomBytes(AES_GCM_IV_LEN);
        byte[] ct = aesGcmEncrypt(masterKey, iv, plaintext);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return b64urlEncode(out);
    }

    public static byte[] openWithMaster(byte[] masterKey, String envelopeB64url) {
        byte[] buf = b64urlDecode(envelopeB64url);
        if (buf.length <= AES_GCM_IV_LEN) {
            throw new IllegalArgumentException("envelope 长度异常,不足以容纳 iv + ciphertext");
        }
        byte[] iv = Arrays.copyOfRange(buf, 0, AES_GCM_IV_LEN);
        byte[] ct = Arrays.copyOfRange(buf, AES_GCM_IV_LEN, buf.length);
        return aesGcmDecrypt(masterKey, iv, ct);
    }

    // ============ ECDH + HKDF → 会话密钥 ============

    /**
     * 由身份私钥 + 对端身份公钥 派生 A↔B 长期会话密钥。两端按 minMaxAccountIds 数值
     * 排序后拼 info,对称等价。
     */
    public static byte[] deriveSessionKey(byte[] myPrivKey, String peerPubKeyB64url,
                                          String myAccountId, String peerAccountId) {
        try {
            byte[] peerPub = b64urlDecode(peerPubKeyB64url);
            PrivateKey priv = rawToPrivateKey(myPrivKey);
            PublicKey pub = rawToPublicKey(peerPub);
            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(priv);
            ka.doPhase(pub, true);
            byte[] shared = ka.generateSecret();

            byte[] info = (HKDF_INFO_PREFIX + minMaxAccountIds(myAccountId, peerAccountId))
                    .getBytes(StandardCharsets.UTF_8);
            return hkdfSha256(shared, new byte[0], info, SESSION_KEY_LEN);
        } catch (Exception e) {
            throw new IllegalStateException("派生会话密钥失败", e);
        }
    }

    /**
     * 两个雪花 ID(字符串)数值排序后拼 "min|max"。
     */
    private static String minMaxAccountIds(String a, String b) {
        BigInteger ai = new BigInteger(a);
        BigInteger bi = new BigInteger(b);
        BigInteger lo = ai.compareTo(bi) <= 0 ? ai : bi;
        BigInteger hi = ai.compareTo(bi) <= 0 ? bi : ai;
        return lo.toString() + "|" + hi.toString();
    }

    /**
     * HKDF-SHA256(RFC 5869)。salt 为空时按 §2.2 替换为 hashLen(32) 字节零,
     * 与 WebCrypto/HKDF 行为一致。
     */
    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) {
        try {
            int hashLen = 32;
            byte[] actualSalt = (salt == null || salt.length == 0) ? new byte[hashLen] : salt;

            Mac mac = Mac.getInstance("HmacSHA256");
            // Extract: PRK = HMAC(salt, IKM)
            mac.init(new SecretKeySpec(actualSalt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);

            // Expand: T(0)=空; T(i) = HMAC(PRK, T(i-1) || info || i)
            int n = (outLen + hashLen - 1) / hashLen;
            if (n > 255) {
                throw new IllegalArgumentException("HKDF outLen 超出 255*HashLen 上限");
            }
            byte[] okm = new byte[outLen];
            byte[] t = new byte[0];
            int written = 0;
            for (int i = 1; i <= n; i++) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int copyLen = Math.min(hashLen, outLen - written);
                System.arraycopy(t, 0, okm, written, copyLen);
                written += copyLen;
            }
            return okm;
        } catch (Exception e) {
            throw new IllegalStateException("HKDF-SHA256 失败", e);
        }
    }

    // ============ 私聊消息加解密 ============

    public static final class EncryptedMessage {
        public final String iv;
        public final String ciphertext;

        public EncryptedMessage(String iv, String ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    /**
     * 用会话密钥加密 UTF-8 文本,产出可直接填入 EncryptedEnvelopeDTO 的 iv/ciphertext 字段。
     */
    public static EncryptedMessage encryptMessage(byte[] sessionKey, String plaintext) {
        byte[] iv = randomBytes(AES_GCM_IV_LEN);
        byte[] ct = aesGcmEncrypt(sessionKey, iv, plaintext.getBytes(StandardCharsets.UTF_8));
        return new EncryptedMessage(b64urlEncode(iv), b64urlEncode(ct));
    }

    public static String decryptMessage(byte[] sessionKey, String ivB64url, String ciphertextB64url) {
        byte[] iv = b64urlDecode(ivB64url);
        if (iv.length != AES_GCM_IV_LEN) {
            throw new IllegalArgumentException(
                    "私聊 IV 长度必须 " + AES_GCM_IV_LEN + "B,实际 " + iv.length + "B");
        }
        byte[] ct = b64urlDecode(ciphertextB64url);
        byte[] pt = aesGcmDecrypt(sessionKey, iv, ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) {
        try {
            if (key.length != 32) {
                throw new IllegalArgumentException("AES-256 密钥必须 32B");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    private static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext) {
        try {
            if (key.length != 32) {
                throw new IllegalArgumentException("AES-256 密钥必须 32B");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 解密失败(密钥/IV/密文不匹配?)", e);
        }
    }

    // ============ Fingerprint(安全码) ============

    /**
     * SHA-256(min(pubA,pubB) || max(pubA,pubB)) 前 30B → 6 组 5 位十进制,空格分隔。
     * 排序基于 raw 字节字典序(不是 base64url 字符串)。
     */
    public static String computeFingerprint(String pubKeyAB64url, String pubKeyBB64url) {
        try {
            byte[] a = b64urlDecode(pubKeyAB64url);
            byte[] b = b64urlDecode(pubKeyBB64url);
            int cmp = compareBytes(a, b);
            byte[] concat = new byte[a.length + b.length];
            if (cmp <= 0) {
                System.arraycopy(a, 0, concat, 0, a.length);
                System.arraycopy(b, 0, concat, a.length, b.length);
            } else {
                System.arraycopy(b, 0, concat, 0, b.length);
                System.arraycopy(a, 0, concat, b.length, a.length);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(concat);
            return bytesToDecimalGroups(Arrays.copyOfRange(digest, 0, FINGERPRINT_BYTES));
        } catch (Exception e) {
            throw new IllegalStateException("计算 fingerprint 失败", e);
        }
    }

    /** 5 字节一组 → 数值 mod 100000 → 补零到 5 位,共 6 组。 */
    private static String bytesToDecimalGroups(byte[] bytes) {
        int bytesPerGroup = FINGERPRINT_BYTES / FINGERPRINT_GROUPS;
        BigInteger mod = BigInteger.valueOf(100_000);
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < FINGERPRINT_GROUPS; g++) {
            BigInteger v = BigInteger.ZERO;
            for (int i = 0; i < bytesPerGroup; i++) {
                v = v.shiftLeft(8).or(BigInteger.valueOf(bytes[g * bytesPerGroup + i] & 0xFF));
            }
            String s = v.mod(mod).toString();
            // 补 0 到 5 位
            while (s.length() < FINGERPRINT_GROUP_LEN) {
                s = "0" + s;
            }
            if (g > 0) sb.append(' ');
            sb.append(s);
        }
        return sb.toString();
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) return ai - bi;
        }
        return a.length - b.length;
    }

    // ============ X25519 纯标量*基点(用于 publicKeyFromPrivate) ============
    // JDK 没暴露 "scalar * basepoint",而 KeyAgreement 必须给两个 endpoint。
    // 通过 KeyPairGenerator 也无法注入指定 scalar。所以这里自实现 RFC 7748 §5 标量乘法。
    private static final class X25519 {
        // p = 2^255 - 19
        private static final BigInteger P =
                BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
        // a24 = (486662 - 2) / 4 = 121665
        private static final BigInteger A24 = BigInteger.valueOf(121_665);

        /** 标量(little-endian 32B)乘基点 u=9,返回 raw 32B little-endian 公钥。 */
        static byte[] scalarMultBase(byte[] kBytes) {
            byte[] uBytes = new byte[32];
            uBytes[0] = 9;
            return scalarMult(kBytes, uBytes);
        }

        /** RFC 7748 §5: X25519(k, u) → 32B little-endian。 */
        static byte[] scalarMult(byte[] kBytes, byte[] uBytes) {
            // 解码 + clamp scalar
            byte[] k = kBytes.clone();
            k[0] &= (byte) 248;
            k[31] &= (byte) 127;
            k[31] |= (byte) 64;
            BigInteger kScalar = littleEndianToBigInt(k);

            // u 坐标(去掉最高 bit,标准要求)
            byte[] uClone = uBytes.clone();
            uClone[31] &= (byte) 127;
            BigInteger u = littleEndianToBigInt(uClone);
            u = u.mod(P);

            BigInteger x1 = u;
            BigInteger x2 = BigInteger.ONE;
            BigInteger z2 = BigInteger.ZERO;
            BigInteger x3 = u;
            BigInteger z3 = BigInteger.ONE;
            int swap = 0;

            for (int t = 254; t >= 0; t--) {
                int kt = kScalar.testBit(t) ? 1 : 0;
                swap ^= kt;
                if (swap == 1) {
                    BigInteger tmp = x2; x2 = x3; x3 = tmp;
                    tmp = z2; z2 = z3; z3 = tmp;
                }
                swap = kt;

                BigInteger A = x2.add(z2).mod(P);
                BigInteger AA = A.multiply(A).mod(P);
                BigInteger B = x2.subtract(z2).mod(P);
                BigInteger BB = B.multiply(B).mod(P);
                BigInteger E = AA.subtract(BB).mod(P);
                BigInteger C = x3.add(z3).mod(P);
                BigInteger D = x3.subtract(z3).mod(P);
                BigInteger DA = D.multiply(A).mod(P);
                BigInteger CB = C.multiply(B).mod(P);
                x3 = DA.add(CB).mod(P);
                x3 = x3.multiply(x3).mod(P);
                z3 = DA.subtract(CB).mod(P);
                z3 = x1.multiply(z3.multiply(z3).mod(P)).mod(P);
                x2 = AA.multiply(BB).mod(P);
                z2 = E.multiply(AA.add(A24.multiply(E).mod(P)).mod(P)).mod(P);
            }
            if (swap == 1) {
                BigInteger tmp = x2; x2 = x3; x3 = tmp;
                tmp = z2; z2 = z3; z3 = tmp;
            }
            BigInteger result = x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P);
            return bigIntToLittleEndian(result, 32);
        }
    }
}
