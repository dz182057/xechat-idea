package cn.xeblog.plugin.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.handler.message.MessageHandler;
import cn.xeblog.plugin.factory.MessageHandlerFactory;
import lombok.AllArgsConstructor;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@AllArgsConstructor
public class ResponseHandler {

    private Response response;

    public void exec() {
        if (response.getType() == MessageType.HEARTBEAT) {
            return;
        }

        process();
    }

    private void process() {
        MessageHandler handler = MessageHandlerFactory.INSTANCE.produce(response.getType());
        if (handler == null) {
            System.err.println("未注册的消息处理器：" + response.getType());
            return;
        }
        handler.handle(response);
    }

}
