package cn.xeblog.plugin.crypto;

import javax.crypto.AEADBadTagException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * C2 加密原语自回环 + RFC 7748 / RFC 5869 已知向量校验。
 *
 * <p>通过 {@code ./gradlew :xechat-plugin:e2eeCheck} 运行(独立 JavaExec 任务,
 * 绕开 IntelliJ Platform test 集成对 JNA 的强制路径注入)。</p>
 *
 * <p>跨端 (plugin ↔ desktop) 对拍由 F 段联调脚本兜底;本程序只验证 plugin 单端
 * 的算法正确性 + 字节级编码与 RFC 标准一致 — 既然 desktop 端 c1-self-check.mjs
 * 也按 RFC 实现,两端必然等价。</p>
 */
public final class E2EECryptoSelfCheck {

    private static final HexFormat HEX = HexFormat.of();

    private static int pass = 0;
    private static int fail = 0;

    private E2EECryptoSelfCheck() {
    }

    public static void main(String[] args) {
        try {
            base64urlRoundTrip();
            masterSealAndOpen();
            sessionKeySymmetry();
            messageEncryptDecrypt();
            fingerprintOrderIndependent();
            rfc7748Vector();
            sessionKeyDeterministicAcrossEnds();
            hkdfRfc5869Vector();
            fingerprintDeterministicAcrossEnds();
        } catch (Throwable t) {
            bad("未捕获异常: " + t);
            t.printStackTrace();
        }
        System.out.println("\n── 结果: " + pass + " passed, " + fail + " failed ──");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ============ 1. base64url 往返 ============
    private static void base64urlRoundTrip() {
        section("1. base64url roundtrip");
        byte[] raw = new byte[]{0, 1, (byte) 254, (byte) 255, 7, 13, (byte) 200};
        String s = E2EECrypto.b64urlEncode(raw);
        check(!s.contains("=") && !s.contains("+") && !s.contains("/"),
                "字符集合规(无 padding,无 +/)");
        check(Arrays.equals(raw, E2EECrypto.b64urlDecode(s)),
                "encode/decode 还原: \"" + s + "\"");
    }

    // ============ 2. master 包裹/解包 ============
    private static void masterSealAndOpen() {
        section("2. master 包裹/解包还原");
        String salt = E2EECrypto.generateE2eeSalt();
        System.out.println("     生成 e2eeSalt = " + salt + " ("
                + E2EECrypto.b64urlDecode(salt).length + "B)");

        long t0 = System.currentTimeMillis();
        byte[] master = E2EECrypto.deriveMasterKey("hunter2-AbcXyz", salt);
        System.out.println("     Argon2id 派生 master 耗时 "
                + (System.currentTimeMillis() - t0) + " ms,长度 " + master.length + "B");
        check(master.length == 32, "master key 长度 32B");

        E2EECrypto.IdentityKeyPair id = E2EECrypto.generateIdentityKeyPair();
        check(id.privKey.length == 32, "X25519 私钥 32B");
        check(E2EECrypto.b64urlDecode(id.pubKeyB64url).length == 32, "X25519 公钥 32B");
        check(id.pubKeyB64url.equals(E2EECrypto.publicKeyFromPrivate(id.privKey)),
                "publicKeyFromPrivate 与 generate 一致");

        String envelope = E2EECrypto.sealWithMaster(master, id.privKey);
        System.out.println("     私钥信封 (" + envelope.length() + " chars): "
                + envelope.substring(0, Math.min(40, envelope.length())) + "...");
        byte[] opened = E2EECrypto.openWithMaster(master, envelope);
        check(Arrays.equals(id.privKey, opened), "master 包裹→解包还原私钥");

        // 错密码应解失败
        byte[] wrong = E2EECrypto.deriveMasterKey("hunter2-AbcXyz!", salt);
        boolean threw = false;
        Throwable caught = null;
        try {
            E2EECrypto.openWithMaster(wrong, envelope);
        } catch (Exception e) {
            threw = true;
            caught = e.getCause();
        }
        check(threw && (caught instanceof AEADBadTagException
                        || caught instanceof GeneralSecurityException),
                "错误 master 解包按预期抛错(GCM tag 校验)");
    }

    // ============ 3. ECDH+HKDF 双向一致 ============
    private static void sessionKeySymmetry() {
        section("3. A/B ECDH+HKDF 派生同一会话密钥");
        E2EECrypto.IdentityKeyPair A = E2EECrypto.generateIdentityKeyPair();
        E2EECrypto.IdentityKeyPair B = E2EECrypto.generateIdentityKeyPair();
        String accA = "7245678901234567890";
        String accB = "1145678901234567890";

        byte[] kAB = E2EECrypto.deriveSessionKey(A.privKey, B.pubKeyB64url, accA, accB);
        byte[] kBA = E2EECrypto.deriveSessionKey(B.privKey, A.pubKeyB64url, accB, accA);
        check(kAB.length == 32, "会话密钥 32B");
        check(Arrays.equals(kAB, kBA), "A/B 双向派生结果一致(对称等价)");

        byte[] kAB2 = E2EECrypto.deriveSessionKey(A.privKey, B.pubKeyB64url, accB, accA);
        check(Arrays.equals(kAB, kAB2), "info 串 min/max 排序与传入顺序无关");
    }

    // ============ 4. 私聊消息加解密 ============
    private static void messageEncryptDecrypt() {
        section("4. 私聊消息加密/解密往返");
        E2EECrypto.IdentityKeyPair A = E2EECrypto.generateIdentityKeyPair();
        E2EECrypto.IdentityKeyPair B = E2EECrypto.generateIdentityKeyPair();
        byte[] sess = E2EECrypto.deriveSessionKey(A.privKey, B.pubKeyB64url, "100", "200");

        String plain = "hello 你好 🌟 quick brown fox";
        E2EECrypto.EncryptedMessage env = E2EECrypto.encryptMessage(sess, plain);
        System.out.println("     iv(" + E2EECrypto.b64urlDecode(env.iv).length + "B)=" + env.iv);
        System.out.println("     ct(" + E2EECrypto.b64urlDecode(env.ciphertext).length + "B)="
                + env.ciphertext);
        String back = E2EECrypto.decryptMessage(sess, env.iv, env.ciphertext);
        check(plain.equals(back), "加密/解密还原 UTF-8 文本(含中文与 emoji)");

        byte[] wrong = E2EECrypto.deriveSessionKey(A.privKey, B.pubKeyB64url, "100", "999");
        boolean threw = false;
        try {
            E2EECrypto.decryptMessage(wrong, env.iv, env.ciphertext);
        } catch (Exception e) {
            threw = true;
        }
        check(threw, "错误会话密钥解密按预期抛错");
    }

    // ============ 5. Fingerprint 顺序无关 ============
    private static void fingerprintOrderIndependent() {
        section("5. fingerprint 顺序无关性");
        E2EECrypto.IdentityKeyPair A = E2EECrypto.generateIdentityKeyPair();
        E2EECrypto.IdentityKeyPair B = E2EECrypto.generateIdentityKeyPair();
        String fpAB = E2EECrypto.computeFingerprint(A.pubKeyB64url, B.pubKeyB64url);
        String fpBA = E2EECrypto.computeFingerprint(B.pubKeyB64url, A.pubKeyB64url);
        System.out.println("     fp(A,B) = " + fpAB);
        System.out.println("     fp(B,A) = " + fpBA);
        check(fpAB.equals(fpBA), "fingerprint 顺序无关(min||max 排序生效)");
        check(fpAB.matches("^\\d{5} \\d{5} \\d{5} \\d{5} \\d{5} \\d{5}$"),
                "fingerprint 格式为 6 组 5 位空格分隔");
    }

    // ============ 6. RFC 7748 §6.1 已知向量 ============
    private static void rfc7748Vector() {
        section("6. RFC 7748 §6.1 X25519 标准向量");
        byte[] alicePriv = HEX.parseHex(
                "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] alicePubExpected = HEX.parseHex(
                "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a");
        byte[] bobPriv = HEX.parseHex(
                "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        byte[] bobPubExpected = HEX.parseHex(
                "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f");

        byte[] alicePub = E2EECrypto.b64urlDecode(E2EECrypto.publicKeyFromPrivate(alicePriv));
        check(Arrays.equals(alicePubExpected, alicePub),
                "Alice 公钥编码与 RFC 7748 §6.1 一致");
        byte[] bobPub = E2EECrypto.b64urlDecode(E2EECrypto.publicKeyFromPrivate(bobPriv));
        check(Arrays.equals(bobPubExpected, bobPub),
                "Bob 公钥编码与 RFC 7748 §6.1 一致");
    }

    // ============ 7. Alice/Bob session 跨端可对拍 ============
    private static void sessionKeyDeterministicAcrossEnds() {
        section("7. RFC 向量下 session key 确定性(供 F 段跨端对拍)");
        byte[] alicePriv = HEX.parseHex(
                "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] bobPriv = HEX.parseHex(
                "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        String alicePub = E2EECrypto.publicKeyFromPrivate(alicePriv);
        String bobPub = E2EECrypto.publicKeyFromPrivate(bobPriv);
        String idA = "1000000000000000001";
        String idB = "2000000000000000002";

        byte[] sessFromA = E2EECrypto.deriveSessionKey(alicePriv, bobPub, idA, idB);
        byte[] sessFromB = E2EECrypto.deriveSessionKey(bobPriv, alicePub, idB, idA);
        check(Arrays.equals(sessFromA, sessFromB),
                "Alice/Bob 双向派生 session 一致");
        System.out.println("     session key (hex) = " + HEX.formatHex(sessFromA));
        System.out.println("     ← 把这串放进 desktop 同样向量下校验,字节相等即跨端等价");
    }

    // ============ 9. RFC 向量下 fingerprint 字节级常量(F1 跨端对拍) ============
    private static void fingerprintDeterministicAcrossEnds() {
        section("9. RFC 向量下 fingerprint 字节级常量(F1 跨端对拍)");
        // 用 RFC 7748 §6.1 标准向量产出的 Alice/Bob 公钥算 fingerprint,
        // desktop c1-self-check.mjs 必须算出相同的串(48180 99430 54812 77458 62055 38291),
        // 任何一端偏离 → 用户在两端看到不同安全码 → 无法面对面核对。
        byte[] alicePriv = HEX.parseHex(
                "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] bobPriv = HEX.parseHex(
                "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        String alicePub = E2EECrypto.publicKeyFromPrivate(alicePriv);
        String bobPub = E2EECrypto.publicKeyFromPrivate(bobPriv);

        String fp = E2EECrypto.computeFingerprint(alicePub, bobPub);
        String expected = "48180 99430 54812 77458 62055 38291";
        System.out.println("     plugin  fingerprint = \"" + fp + "\"");
        System.out.println("     desktop fingerprint = \"" + expected + "\"");
        check(expected.equals(fp), "★★★ fingerprint 跨端字节级一致(F1)");
    }

    // ============ 8. HKDF-SHA256 RFC 5869 §A.1 ============
    private static void hkdfRfc5869Vector() {
        section("8. HKDF-SHA256 RFC 5869 §A.1 已知向量");
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
        byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");
        byte[] expected = HEX.parseHex(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                        + "34007208d5b887185865");
        byte[] out = E2EECrypto.hkdfSha256(ikm, salt, info, 42);
        check(Arrays.equals(expected, out),
                "HKDF-SHA256 与 RFC 5869 §A.1 一致");
    }

    // ============ 工具 ============
    private static void section(String title) {
        System.out.println("\n── " + title + " ──");
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            pass++;
            System.out.println("  [OK] " + msg);
        } else {
            fail++;
            System.out.println("  [FAIL] " + msg);
        }
    }

    private static void bad(String msg) {
        fail++;
        System.out.println("  [FAIL] " + msg);
    }
}
