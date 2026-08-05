package cn.xeblog.server.handler;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.server.account.SessionService;
import cn.xeblog.server.account.entity.SessionEntity;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.duo.DuoAttachmentService;
import cn.xeblog.server.duo.DuoSpaceService;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 在 HTTP 聚合器之前流式接收双人小屋附件，避免大请求先占满聚合缓冲区。
 */
public final class DuoAttachmentUploadHandler extends ChannelInboundHandlerAdapter {

    private UploadState upload;
    private boolean discarding;

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        closeUploadTemp();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        closeUploadTemp();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        discarding = true;
        closeUploadTemp();
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (discarding) {
            if (msg instanceof HttpObject) ReferenceCountUtil.release(msg);
            return;
        }

        if (upload == null && msg instanceof HttpRequest) {
            UploadTarget target = parseTarget((HttpRequest) msg);
            if (target == null) {
                ctx.fireChannelRead(msg);
                return;
            }
            if (!begin(ctx, (HttpRequest) msg, target)) {
                ReferenceCountUtil.release(msg);
                return;
            }
            if (msg instanceof io.netty.handler.codec.http.HttpContent) {
                try {
                    receive(ctx, (io.netty.handler.codec.http.HttpContent) msg);
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            } else {
                ReferenceCountUtil.release(msg);
            }
            return;
        }

        if (upload != null && msg instanceof io.netty.handler.codec.http.HttpContent) {
            try {
                receive(ctx, (io.netty.handler.codec.http.HttpContent) msg);
            } finally {
                ReferenceCountUtil.release(msg);
            }
            return;
        }

        ctx.fireChannelRead(msg);
    }

    private boolean begin(ChannelHandlerContext ctx, HttpRequest request, UploadTarget target) {
        long declaredLength = HttpUtil.getContentLength(request, -1L);
        if (declaredLength > DuoAttachmentService.MAX_BYTES) {
            rejectAndClose(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "附件超过大小限制");
            return false;
        }
        try {
            UUID.fromString(target.spaceId);
            UUID.fromString(target.attachmentId);
        } catch (IllegalArgumentException e) {
            rejectAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "附件标识格式不正确");
            return false;
        }

        User user = resolveHttpUser(request);
        if (user == null || user.isGuest() || user.getAccountId() <= 0L) {
            rejectAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, "请先登录账号");
            return false;
        }
        if (!DuoSpaceService.isActiveMember(user.getAccountId(), target.spaceId)) {
            rejectAndClose(ctx, HttpResponseStatus.FORBIDDEN, "没有权限访问该小屋");
            return false;
        }

        try {
            DuoAttachmentService.ensureDirectory();
            Path directory = Path.of(GlobalConfig.DUO_ATTACHMENT_DIR).toAbsolutePath().normalize();
            Path temp = Files.createTempFile(directory, "upload-", ".tmp");
            upload = new UploadState(user.getAccountId(), target.spaceId, target.attachmentId, temp,
                    Files.newOutputStream(temp, StandardOpenOption.WRITE));
            if (HttpUtil.is100ContinueExpected(request)) {
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            }
            return true;
        } catch (Exception e) {
            rejectAndClose(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "附件上传失败");
            return false;
        }
    }

    private void receive(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpContent content) {
        if (upload == null) return;
        ByteBuf buffer = content.content();
        int readable = buffer.readableBytes();
        if (upload.received + readable > DuoAttachmentService.MAX_BYTES) {
            closeUploadTemp();
            discarding = true;
            rejectAndClose(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "附件超过大小限制");
            return;
        }
        try {
            buffer.readBytes(upload.output, readable);
            upload.received += readable;
            if (content instanceof LastHttpContent) finish(ctx);
        } catch (Exception e) {
            closeUploadTemp();
            discarding = true;
            rejectAndClose(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "附件上传失败");
        }
    }

    private void finish(ChannelHandlerContext ctx) {
        UploadState completed = upload;
        upload = null;
        try {
            completed.output.close();
            try (InputStream input = Files.newInputStream(completed.temp, StandardOpenOption.READ)) {
                DuoAttachmentService.upload(completed.accountId, completed.spaceId, completed.attachmentId,
                        input, completed.received);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("attachmentId", completed.attachmentId);
            writeResult(ctx, true, "上传成功", data, HttpResponseStatus.CREATED, false);
        } catch (DuoAttachmentService.ForbiddenException e) {
            writeResult(ctx, false, "没有权限访问该小屋", null, HttpResponseStatus.FORBIDDEN, true);
        } catch (DuoAttachmentService.ConflictException e) {
            writeResult(ctx, false, "附件标识已存在", null, HttpResponseStatus.CONFLICT, true);
        } catch (DuoAttachmentService.PayloadTooLargeException e) {
            writeResult(ctx, false, "附件超过大小限制", null, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, true);
        } catch (IllegalArgumentException e) {
            writeResult(ctx, false, e.getMessage(), null, HttpResponseStatus.BAD_REQUEST, true);
        } catch (Exception e) {
            writeResult(ctx, false, "附件上传失败", null, HttpResponseStatus.INTERNAL_SERVER_ERROR, true);
        } finally {
            deleteTemp(completed.temp);
        }
    }

    private void closeUploadTemp() {
        if (upload == null) return;
        try {
            upload.output.close();
        } catch (Exception ignored) {
            // 清理阶段无需再次上抛异常
        }
        deleteTemp(upload.temp);
        upload = null;
    }

    private void rejectAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        discarding = true;
        writeResult(ctx, false, message, null, status, true);
    }

    private void writeResult(ChannelHandlerContext ctx, boolean succeed, String message, Object data,
                             HttpResponseStatus status, boolean close) {
        Map<String, Object> result = new HashMap<>();
        result.put("succeed", succeed);
        result.put("msg", message);
        result.put("data", data);
        byte[] body = JSONUtil.toJsonStr(result).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONNECTION, close ? HttpHeaderValues.CLOSE : HttpHeaderValues.KEEP_ALIVE);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization,Content-Type");
        response.content().writeBytes(body);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(close ? ChannelFutureListener.CLOSE : ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private UploadTarget parseTarget(HttpRequest request) {
        if (request.method() != HttpMethod.POST) return null;
        String uri = request.uri();
        int queryIndex = uri.indexOf('?');
        String path = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
        String[] parts = path.split("/", -1);
        if (parts.length != 6 || !"api".equals(parts[1]) || !"duo-spaces".equals(parts[2])
                || !"attachments".equals(parts[4])) {
            return null;
        }
        return new UploadTarget(parts[3], parts[5]);
    }

    private User resolveHttpUser(HttpRequest request) {
        String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()) : null;
        SessionEntity session = SessionService.validateAndTouch(token);
        if (session == null) return null;
        java.util.List<User> users = UserCache.getByAccount(session.getAccountId());
        return users.isEmpty() ? null : users.get(0);
    }

    private void deleteTemp(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 临时文件清理失败由孤儿附件清理任务兜底
        }
    }

    private static final class UploadTarget {
        private final String spaceId;
        private final String attachmentId;

        private UploadTarget(String spaceId, String attachmentId) {
            this.spaceId = spaceId;
            this.attachmentId = attachmentId;
        }
    }

    private static final class UploadState {
        private final long accountId;
        private final String spaceId;
        private final String attachmentId;
        private final Path temp;
        private final OutputStream output;
        private long received;

        private UploadState(long accountId, String spaceId, String attachmentId, Path temp,
                            OutputStream output) {
            this.accountId = accountId;
            this.spaceId = spaceId;
            this.attachmentId = attachmentId;
            this.temp = temp;
            this.output = output;
        }
    }
}
