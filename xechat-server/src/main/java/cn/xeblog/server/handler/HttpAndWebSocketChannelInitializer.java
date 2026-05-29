package cn.xeblog.server.handler;

import cn.xeblog.server.codec.WebSocketMessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @author anlingyi
 * @date 2023/8/31 8:33 PM
 */
public class HttpAndWebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_WEBSOCKET_FRAME_BYTES = 512 * 1024;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast(new IdleStateHandler(0, 0, 60))
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(4 * 1024 * 1024))
                .addLast(new ChunkedWriteHandler())
                .addLast(new WebSocketMessageEncoder())
                .addLast(new WebSocketServerCompressionHandler())
                .addLast(new WebSocketServerProtocolHandler("/xechat", null, true, MAX_WEBSOCKET_FRAME_BYTES))
                .addLast(new HttpChannelHandler())
                .addLast(new WebSocketChannelHandler());
    }

}
