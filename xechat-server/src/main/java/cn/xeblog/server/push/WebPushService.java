package cn.xeblog.server.push;

import cn.hutool.json.JSONUtil;
import cn.xeblog.server.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public final class WebPushService {

    private static final String KEY_FILE = "web-push-vapid.properties";
    private static final String SUBJECT = "https://129.211.26.139/";
    private static final int TTL_SECONDS = 60;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "xechat-web-push");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile VapidKeys keys;

    private WebPushService() {
    }

    public static String publicKey() {
        return keys().publicKey;
    }

    public static void pushPrivateMessage(long recipientAccountId, String senderName) {
        if (recipientAccountId <= 0) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PRIVATE_MESSAGE");
        payload.put("title", senderName == null || senderName.trim().isEmpty() ? "XeChat 私聊" : senderName);
        payload.put("body", "收到一条私聊消息");
        payload.put("url", "/");
        pushToAccount(recipientAccountId, JSONUtil.toJsonStr(payload));
    }

    public static void pushToAccount(long accountId, String payload) {
        List<PushSubscriptionEntity> subscriptions = PushSubscriptionService.listByAccount(accountId);
        log.info("准备发送 Web Push accountId={} subscriptionCount={}", accountId, subscriptions.size());
        for (PushSubscriptionEntity subscription : subscriptions) {
            EXECUTOR.execute(() -> sendOne(subscription, payload));
        }
    }

    private static void sendOne(PushSubscriptionEntity subscription, String payload) {
        try {
            VapidKeys vapid = keys();
            PushService service = new PushService(vapid.publicKey, vapid.privateKey, SUBJECT);
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8),
                    TTL_SECONDS);
            HttpResponse response = service.send(notification);
            int status = response.getStatusLine().getStatusCode();
            log.info("Web Push 发送完成 accountId={} status={} reason={}",
                    subscription.getAccountId(), status, response.getStatusLine().getReasonPhrase());
            if (status == 404 || status == 410) {
                PushSubscriptionService.deleteEndpoint(subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("发送 Web Push 失败 endpoint={}", subscription.getEndpoint(), e);
        }
    }

    private static VapidKeys keys() {
        if (keys == null) {
            synchronized (WebPushService.class) {
                if (keys == null) {
                    keys = loadOrCreateKeys();
                }
            }
        }
        return keys;
    }

    private static VapidKeys loadOrCreateKeys() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Files.createDirectories(Paths.get(GlobalConfig.DATA_DIR));
            Path path = Paths.get(GlobalConfig.DATA_DIR, KEY_FILE);
            if (Files.exists(path)) {
                Properties props = new Properties();
                try (java.io.InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                }
                String publicKey = props.getProperty("publicKey");
                String privateKey = props.getProperty("privateKey");
                if (!isBlank(publicKey) && !isBlank(privateKey)) {
                    return new VapidKeys(publicKey, privateKey);
                }
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
            generator.initialize(new ECGenParameterSpec("prime256v1"));
            KeyPair pair = generator.generateKeyPair();
            String publicKey = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
            String privateKey = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));

            Properties props = new Properties();
            props.setProperty("publicKey", publicKey);
            props.setProperty("privateKey", privateKey);
            try (java.io.OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "XeChat Web Push VAPID keys");
            }
            return new VapidKeys(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 Web Push VAPID 密钥失败", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class VapidKeys {
        private final String publicKey;
        private final String privateKey;

        private VapidKeys(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }
}
