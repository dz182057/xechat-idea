package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Platform;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserCacheClientSessionTest {

    @After
    public void tearDown() {
        UserCache.clear();
    }

    @Test
    public void sameAccountCanBeOnlineOncePerPlatform() {
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
    public void sameAccountAndIdeaPlatformCanOnlyBeOnlineOnce() {
        User first = user("channel-1", 1001L, "client-uuid-1", Platform.IDEA);
        User duplicateIdea = user("channel-2", 1001L, "client-uuid-2", Platform.IDEA);
        User web = user("channel-3", 1001L, "client-uuid-3", Platform.WEB);

        assertTrue(UserCache.tryAcquireAccountClient(first));
        UserCache.add(first.getId(), first);

        assertFalse(UserCache.tryAcquireAccountClient(duplicateIdea));
        assertTrue(UserCache.tryAcquireAccountClient(web));
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

    @Test
    public void addingReplacementAccountClientOnlyReturnsSamePlatformOldSession() {
        User desktop = user("channel-1", 1001L, "desktop-uuid-1", Platform.DESKTOP);
        User idea = user("channel-2", 1001L, "idea-uuid-1", Platform.IDEA);
        User nextDesktop = user("channel-3", 1001L, "desktop-uuid-2", Platform.DESKTOP);

        assertTrue(UserCache.addReplacingAccountClient(desktop).isEmpty());
        assertTrue(UserCache.addReplacingAccountClient(idea).isEmpty());

        List<User> kickedUsers = UserCache.addReplacingAccountClient(nextDesktop);

        assertEquals(1, kickedUsers.size());
        assertEquals("channel-1", kickedUsers.get(0).getId());
        assertEquals(3, UserCache.getByAccount(1001L).size());

        UserCache.remove(desktop.getId());

        assertEquals(2, UserCache.getByAccount(1001L).size());
        assertTrue(UserCache.getByAccount(1001L).contains(idea));
        assertTrue(UserCache.getByAccount(1001L).contains(nextDesktop));
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
