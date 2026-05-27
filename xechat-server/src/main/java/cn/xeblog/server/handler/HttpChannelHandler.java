package cn.xeblog.server.handler;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.react.result.ReactResult;
import cn.xeblog.commons.entity.react.result.UploadReactResult;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.AvatarService;
import cn.xeblog.server.account.SessionService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.entity.SessionEntity;
import cn.xeblog.server.action.handler.react.UploadReactHandler;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.util.FileUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

import java.util.List;

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
        setCorsHeaders(response);

        String uri = fullHttpRequest.uri();
        // ? 之后是 query string,先剥离
        int queryIdx = uri.indexOf('?');
        String path = queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;

        if (fullHttpRequest.method() == HttpMethod.OPTIONS) {
            response.setStatus(HttpResponseStatus.NO_CONTENT);
        } else if (path.equals("/upload") && fullHttpRequest.method() == HttpMethod.POST) {
            handleUpload(fullHttpRequest, response);
        } else if (path.startsWith("/download/")) {
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

    private void setCorsHeaders(FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization,Content-Type");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");
    }

    private void handleUpload(FullHttpRequest request, FullHttpResponse response) {
        ReactResult<UploadReactResult> result = new ReactResult<>();
        result.setSucceed(false);
        result.setMsg("上传失败");

        HttpPostRequestDecoder decoder = null;
        try {
            decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);
            byte[] bytes = null;
            String fileType = null;
            String uid = null;
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    FileUpload upload = (FileUpload) data;
                    bytes = new byte[(int) upload.length()];
                    upload.getByteBuf().getBytes(upload.getByteBuf().readerIndex(), bytes);
                    if (fileType == null) {
                        fileType = extensionOf(upload.getFilename());
                    }
                } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attr = (Attribute) data;
                    if ("fileType".equals(attr.getName())) {
                        fileType = attr.getValue();
                    } else if ("uid".equals(attr.getName())) {
                        uid = attr.getValue();
                    }
                }
            }
            User user = resolveUploadUser(request, uid);
            if (user == null) {
                result.setMsg("请先登录");
                writeJson(response, result, HttpResponseStatus.UNAUTHORIZED);
                return;
            }
            if (bytes == null || bytes.length == 0) {
                result.setMsg("请选择图片文件");
                writeJson(response, result, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            UploadReactHandler.saveImage(user, fileType, bytes, result);
            writeJson(response, result, result.isSucceed() ? HttpResponseStatus.OK : HttpResponseStatus.BAD_REQUEST);
        } catch (Exception e) {
            result.setMsg("图片上传失败");
            writeJson(response, result, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (decoder != null) {
                decoder.destroy();
            }
        }
    }

    private User resolveUploadUser(FullHttpRequest request, String uid) {
        String token = bearerToken(request.headers().get(HttpHeaderNames.AUTHORIZATION));
        SessionEntity session = SessionService.validateAndTouch(token);
        if (session != null) {
            List<User> users = UserCache.getByAccount(session.getAccountId());
            if (!users.isEmpty()) {
                return users.get(0);
            }
        }
        return uid == null ? null : UserCache.get(uid);
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        return authorization.startsWith(prefix) ? authorization.substring(prefix.length()) : null;
    }

    private String extensionOf(String fileName) {
        int idx = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "png";
        }
        return fileName.substring(idx + 1);
    }

    private void writeJson(FullHttpResponse response, Object body, HttpResponseStatus status) {
        response.setStatus(status);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.content().writeBytes(JSONUtil.toJsonStr(body).getBytes(CharsetUtil.UTF_8));
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
