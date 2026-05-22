package cn.xeblog.server.handler;

import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.AvatarService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.util.FileUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

/**
 * @author anlingyi
 * @date 2023/9/1 9:04 PM
 */
public class HttpChannelHandler extends AbstractDefaultChannelHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");

        String uri = fullHttpRequest.uri();
        // ? 之后是 query string,先剥离
        int queryIdx = uri.indexOf('?');
        String path = queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;

        if (path.startsWith("/download/")) {
            String fileName = path.replace("/download/", "");
            if (!fileName.startsWith(".")) {
                byte[] bytes = FileUtil.getFile(fileName);
                if (bytes != null) {
                    response.content().writeBytes(bytes);
                    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/jpeg,image/png,image/gif,image/bmp");
                    response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline;filename=\"" + fileName + "\"");
                    response.setStatus(HttpResponseStatus.OK);
                }
            }
        } else if (path.startsWith("/avatar/")) {
            handleAvatar(path, response);
        } else {
            response.setStatus(HttpResponseStatus.OK);
            response.content().writeBytes("Hello World!".getBytes(CharsetUtil.UTF_8));
        }

        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * /avatar/{accountId} 路由:
     * - 文件存在 → 返回 PNG + 长 immutable 缓存(客户端用 ?v= 破缓存)
     * - 文件不存在 → 根据账号昵称生成默认色块头像
     * - accountId 解析失败或账号不存在 → 用 "?" 兜底生成默认头像
     */
    private void handleAvatar(String path, FullHttpResponse response) {
        String idStr = path.substring("/avatar/".length());
        long accountId;
        try {
            accountId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return;
        }

        byte[] bytes = AvatarService.readAvatar(accountId);
        if (bytes == null) {
            Account a = AccountService.findById(accountId);
            String nickname = a == null ? "?" : a.getNickname();
            bytes = AvatarService.getOrGenerateDefault(nickname);
        }

        response.content().writeBytes(bytes);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        // 客户端 src 用 ?v={avatarVersion} 破缓存,因此可以永久缓存
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=31536000, immutable");
        response.setStatus(HttpResponseStatus.OK);
    }

}
