package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.duo.DuoSpaceRequestDTO;
import cn.xeblog.commons.entity.duo.DuoSpaceResponseDTO;
import cn.xeblog.commons.enums.DuoSpaceAction;
import cn.xeblog.commons.enums.DuoSpaceEvent;
import cn.xeblog.commons.enums.DuoSpaceStatus;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.duo.DuoSpaceService;
import cn.xeblog.server.duo.DuoTestSupport;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 双人小屋 WebSocket 动作路由和错误响应回归测试。 */
public class DuoSpaceActionHandlerTest {

    @Before
    public void setUp() throws Exception {
        DuoTestSupport.setUpDatabase();
    }

    @After
    public void tearDown() throws Exception {
        DuoTestSupport.tearDownDatabase();
    }

    @Test
    public void profileRequestReturnsTypedDuoResponseWithRequestId() {
        User user = DuoTestSupport.user(DuoTestSupport.ACCOUNT_A, "duo-handler-profile");
        DuoSpaceRequestDTO request = new DuoSpaceRequestDTO();
        request.setAction(DuoSpaceAction.PROFILE);
        request.setRequestId("profile-1");

        new DuoSpaceActionHandler().process(user, request);

        Response<?> response = ((EmbeddedChannel) user.getChannel()).readOutbound();
        assertEquals(MessageType.DUO_SPACE, response.getType());
        DuoSpaceResponseDTO body = (DuoSpaceResponseDTO) response.getBody();
        assertEquals(DuoSpaceEvent.PROFILE, body.getEvent());
        assertEquals("profile-1", body.getRequestId());
        assertEquals(DuoSpaceStatus.NONE, body.getProfile().getStatus());
    }

    @Test
    public void serviceValidationErrorIsReturnedAsSystemMessage() {
        User user = DuoTestSupport.user(DuoTestSupport.ACCOUNT_A, "duo-handler-error");
        DuoSpaceRequestDTO request = new DuoSpaceRequestDTO();
        request.setAction(DuoSpaceAction.INVITE);
        request.setPartnerAccountId(9999L);

        new DuoSpaceActionHandler().process(user, request);

        Response<?> response = ((EmbeddedChannel) user.getChannel()).readOutbound();
        assertEquals(MessageType.SYSTEM, response.getType());
        assertEquals(DuoSpaceService.ERROR_INVITE_FRIEND, response.getBody());
    }

    @Test
    public void memoriesRequestReturnsMemoryEventAfterInvitationAccepted() {
        DuoTestSupport.activateSpace();
        User user = DuoTestSupport.user(DuoTestSupport.ACCOUNT_A, "duo-handler-memories");
        DuoSpaceRequestDTO request = new DuoSpaceRequestDTO();
        request.setAction(DuoSpaceAction.LIST_MEMORIES);
        request.setRequestId("memories-1");

        new DuoSpaceActionHandler().process(user, request);

        Response<?> response = ((EmbeddedChannel) user.getChannel()).readOutbound();
        assertEquals(MessageType.DUO_SPACE, response.getType());
        DuoSpaceResponseDTO body = (DuoSpaceResponseDTO) response.getBody();
        assertEquals(DuoSpaceEvent.MEMORIES, body.getEvent());
        assertEquals("memories-1", body.getRequestId());
        assertTrue(body.getMemories().getItems() != null);
    }
}
