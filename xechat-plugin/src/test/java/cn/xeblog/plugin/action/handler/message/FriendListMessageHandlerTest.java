package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.commons.enums.Platform;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class FriendListMessageHandlerTest {

    @Test
    public void buildOnlineChangedMessageIncludesResponseTime() {
        FriendDTO friend = new FriendDTO(1L, "koma", "こまつ", 0,
                true, Collections.singleton(Platform.WEB));

        String msg = FriendListMessageHandler.buildOnlineChangedMessage("06/11 11:37:25", friend);

        Assert.assertEquals("[06/11 11:37:25] 好友 こまつ 已上线 (Web)", msg);
    }

    @Test
    public void buildOfflineChangedMessageIncludesResponseTime() {
        FriendDTO friend = new FriendDTO(1L, "koma", "こまつ", 0,
                false, Collections.emptySet());

        String msg = FriendListMessageHandler.buildOnlineChangedMessage("06/11 11:38:06", friend);

        Assert.assertEquals("[06/11 11:38:06] 好友 こまつ 已离线", msg);
    }
}
