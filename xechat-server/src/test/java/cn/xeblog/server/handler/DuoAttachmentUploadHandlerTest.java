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
}
