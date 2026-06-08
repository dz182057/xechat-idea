package cn.xeblog.server.friend;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.cache.UserCache;
import org.junit.After;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FriendStealthSessionTest {

    @After
    public void tearDown() {
        UserCache.clear();
    }

    @Test
    public void friendOnlineStateUsesCurrentSessionStealth() {
        User visibleSession = new User();
        visibleSession.setId("desktop-channel");
        visibleSession.setAccountId(1001L);
        visibleSession.setPlatform(Platform.DESKTOP);
        visibleSession.setStealth(false);
        UserCache.add(visibleSession.getId(), visibleSession);

        Set<Platform> platforms = FriendService.collectVisiblePlatforms(1001L);

        assertEquals(1, platforms.size());
        assertTrue(platforms.contains(Platform.DESKTOP));
    }

    @Test
    public void friendOnlineStateHidesStealthSession() {
        User stealthSession = new User();
        stealthSession.setId("desktop-channel");
        stealthSession.setAccountId(1001L);
        stealthSession.setPlatform(Platform.DESKTOP);
        stealthSession.setStealth(true);
        UserCache.add(stealthSession.getId(), stealthSession);

        Set<Platform> platforms = FriendService.collectVisiblePlatforms(1001L);

        assertTrue(platforms.isEmpty());
    }

}
