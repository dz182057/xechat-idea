package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;

/**
 * 消息撤回广播。
 *
 * @author dz
 * @date 2026/5/27
 */
@DoMessage(MessageType.MESSAGE_RECALLED)
public class MessageRecalledHandler extends AbstractMessageHandler<RecallMessageDTO> {

    @Override
    protected void process(Response<RecallMessageDTO> response) {
        RecallMessageDTO body = response.getBody();
        if (body != null) {
            ConsoleAction.markMessageRecalled(body.getMessageId());
        }
        String name = response.getUser() == null ? "有人" : response.getUser().getUsername();
        ConsoleAction.showSimpleMsg(name + " 撤回了一条消息");
    }

}
