package cn.xeblog.server.codec;

import cn.xeblog.commons.entity.FriendRequestDTO;
import cn.xeblog.commons.entity.FriendRequestListMsgDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.builder.ResponseBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class WebSocketMessageEncoderTest {

    @Test
    public void shouldSerializeFriendRequestIdsAsStrings() {
        long unsafeId = 1996110512445652992L;
        FriendRequestDTO request = new FriendRequestDTO(unsafeId, unsafeId + 1,
                "zhangsan", "张三", 1, 1710000000000L);
        FriendRequestListMsgDTO body = new FriendRequestListMsgDTO(
                Collections.singletonList(request));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketMessageEncoder());

        assertTrue(channel.writeOutbound(ResponseBuilder.build(null, body,
                MessageType.FRIEND_REQUEST_LIST)));
        TextWebSocketFrame frame = channel.readOutbound();
        String json = frame.text();

        assertTrue(json, json.contains("\"requestId\":\"" + unsafeId + "\""));
        assertTrue(json, json.contains("\"fromAccountId\":\"" + (unsafeId + 1) + "\""));

        frame.release();
        channel.finishAndReleaseAll();
    }

}
