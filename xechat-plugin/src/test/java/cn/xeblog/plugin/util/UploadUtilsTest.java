package cn.xeblog.plugin.util;

import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class UploadUtilsTest {

    @After
    public void tearDown() {
        DataCache.userMap.clear();
        DataCache.friendMap.clear();
        DataCache.peerAccountByUsername.clear();
    }

    @Test
    public void resolvePrivatePeerAccountShouldUseOfflineFriend() {
        DataCache.friendMap.put("こまつ",
                new FriendDTO(1L, "koma", "こまつ", 0, false, Collections.emptySet()));

        String account = UploadUtils.resolvePrivatePeerAccount("こまつ");

        Assert.assertEquals("koma", account);
        Assert.assertEquals("koma", DataCache.peerAccountByUsername.get("こまつ"));
    }
}
