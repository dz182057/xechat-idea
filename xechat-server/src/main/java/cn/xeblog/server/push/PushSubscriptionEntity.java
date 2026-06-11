package cn.xeblog.server.push;

public class PushSubscriptionEntity {

    private long accountId;
    private String endpoint;
    private String p256dh;
    private String auth;

    public PushSubscriptionEntity(long accountId, String endpoint, String p256dh, String auth) {
        this.accountId = accountId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
