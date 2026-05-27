package cn.xeblog.server.action.handler;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.react.request.ReactRequest;
import cn.xeblog.commons.entity.react.result.ReactResult;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.react.ReactHandler;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.factory.ReactHandlerFactory;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author anlingyi
 * @date 2022/9/19 8:37 AM
 */
@Slf4j
@DoAction(Action.REACT)
public class ReactActionHandler implements ActionHandler<ReactRequest> {

    @Override
    public void handle(ChannelHandlerContext ctx, ReactRequest request) {
        ReactResult result = new ReactResult();
        result.setSucceed(false);
        result.setId(request.getId());
        result.setUid(request.getUid());
        result.setMsg("请求无响应！");

        User user = ChannelAction.getUser(ctx);
        if (user == null) {
            user = UserCache.get(request.getUid());
        }
        if (user == null) {
            result.setMsg("请先登录！");
        } else {
            try {
                ReactHandler handler = ReactHandlerFactory.INSTANCE.produce(request.getReact());
                Object body = request.getBody();
                Class<?> bodyClass = ClassUtil.getTypeArgument(handler.getClass());
                if (body != null) {
                    if (bodyClass != null && !bodyClass.isInstance(body)) {
                        body = JSONUtil.toBean(body.toString(), bodyClass);
                    }
                }
                handler.handle(user, body, result);
            } catch (Exception e) {
                log.error("React请求处理异常", e);
            }
        }

        ctx.writeAndFlush(ResponseBuilder.react(result));
    }

}
