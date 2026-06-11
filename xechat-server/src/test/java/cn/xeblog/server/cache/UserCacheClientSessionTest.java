package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Platform;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserCacheClientSessionTest {

    @After
    public void tearDown() {
        UserCache.clear();
    }

    @Test
    public void sameAccountAndUuidCanOnlyBeOnlineOnce() {
        User first = user("channel-1", 1001L, "client-uuid-1", Platform.DESKTOP);
        User duplicate = user("channel-2", 1001L, "client-uuid-1", Platform.DESKTOP);
        User otherPlatform = user("channel-3", 1001L, "client-uuid-2", Platform.IDEA);

        assertTrue(UserCache.tryAcquireAccountClient(first));
        UserCache.add(first.getId(), first);

        assertFalse(UserCache.tryAcquireAccountClient(duplicate));
        assertTrue(UserCache.tryAcquireAccountClient(otherPlatform));
    }

    @Test
    public void sameAccountAndDesktopPlatformCanOnlyBeOnlineOnce() {
        User first = user("channel-1", 1001L, "client-uuid-1", Platform.DESKTOP);
        User duplicateDesktop = user("channel-2", 1001L, "client-uuid-2", Platform.DESKTOP);
        User otherAccount = user("channel-3", 1002L, "client-uuid-2", Platform.DESKTOP);

        assertTrue(UserCache.tryAcquireAccountClient(first));
        UserCache.add(first.getId(), first);

        assertFalse(UserCache.tryAcquireAccountClient(duplicateDesktop));
        assertTrue(UserCache.tryAcquireAccountClient(otherAccount));
    }

    @Test
    public void removedAccountClientCanLoginAgain() {
        User first = user("channel-1", 1001L, "client-uuid-1", Platform.DESKTOP);
        User next = user("channel-2", 1001L, "client-uuid-1", Platform.DESKTOP);

        assertTrue(UserCache.tryAcquireAccountClient(first));
        UserCache.add(first.getId(), first);
        UserCache.remove(first.getId());

        assertTrue(UserCache.tryAcquireAccountClient(next));
    }

    private static User user(String channelId, long accountId, String uuid, Platform platform) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setUuid(uuid);
        user.setPlatform(platform);
        return user;
    }

}
