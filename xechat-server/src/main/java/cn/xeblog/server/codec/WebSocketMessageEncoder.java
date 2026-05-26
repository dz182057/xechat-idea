package cn.xeblog.server.codec;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.List;
import java.util.regex.Pattern;

/**
 * WebSocket消息编码
 *
 * @author anlingyi
 * @date 2023/9/2 12:48 AM
 */
public class WebSocketMessageEncoder extends MessageToMessageEncoder<Response> {

    /**
     * 把 long 雪花 ID 字段序列化为 JSON string,避免 JS Number 精度丢失。
     * 匹配所有以 AccountId 结尾的字段(accountId / peerAccountId / senderAccountId /
     * recipientAccountId 等)+ createdBy + serverId。服务端内部仍用 long;
     * 入口 hutool 反序列化 string → long 兼容。
     */
    private static final Pattern LONG_ID_FIELD = Pattern.compile(
            "\"(\\w*[Aa]ccountId|createdBy|serverId)\"\\s*:\\s*(-?\\d+)");

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Response response, List<Object> list) throws Exception {
        String json = JSONUtil.toJsonStr(response);
        json = LONG_ID_FIELD.matcher(json).replaceAll("\"$1\":\"$2\"");
        TextWebSocketFrame frame = new TextWebSocketFrame(json);
        list.add(frame);
    }

}
