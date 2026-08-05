package cn.xeblog.server.handler;

import cn.xeblog.server.duo.DuoAttachmentService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import cn.xeblog.server.config.GlobalConfig;

public class DuoAttachmentUploadHandlerTest {

    @Test
    public void rejectsOversizedAttachmentBeforeAuthenticationOrAggregation() {
        EmbeddedChannel channel = new EmbeddedChannel(new DuoAttachmentUploadHandler());
        try {
            DefaultHttpRequest request = new DefaultHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/api/duo-spaces/00000000-0000-0000-0000-000000000001/attachments/"
                            + "00000000-0000-0000-0000-000000000002");
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, DuoAttachmentService.MAX_BYTES + 1);

            channel.writeInbound(request);

            FullHttpResponse response = channel.readOutbound();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
            response.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void channelInactiveDeletesPartialUploadTemp() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new DuoAttachmentUploadHandler());
        Path temp = installPartialUpload(channel.pipeline().get(DuoAttachmentUploadHandler.class));
        try {
            channel.close().syncUninterruptibly();
            Assert.assertFalse(Files.exists(temp));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void handlerRemovedDeletesPartialUploadTemp() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new DuoAttachmentUploadHandler());
        Path temp = installPartialUpload(channel.pipeline().get(DuoAttachmentUploadHandler.class));
        try {
            channel.pipeline().remove(DuoAttachmentUploadHandler.class);
            Assert.assertFalse(Files.exists(temp));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static Path installPartialUpload(DuoAttachmentUploadHandler handler) throws Exception {
        Path directory = Path.of(GlobalConfig.DUO_ATTACHMENT_DIR);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "upload-", ".tmp");
        OutputStream output = Files.newOutputStream(temp);
        Class<?> stateClass = Class.forName(DuoAttachmentUploadHandler.class.getName() + "$UploadState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(
                long.class, String.class, String.class, Path.class, OutputStream.class);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(
                1001L, UUID.randomUUID().toString(), UUID.randomUUID().toString(), temp, output);
        Field uploadField = DuoAttachmentUploadHandler.class.getDeclaredField("upload");
        uploadField.setAccessible(true);
        uploadField.set(handler, state);
        return temp;
    }
}
