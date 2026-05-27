package cn.xeblog.server.action.handler;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Permissions;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.history.MessageHistoryService;
import cn.xeblog.server.util.BaiDuFyUtil;
import cn.xeblog.server.util.SensitiveWordUtils;

/**
 * @author anlingyi
 * @date 2020/8/14
 */
@DoAction(Action.CHAT)
public class ChatActionHandler extends AbstractActionHandler<UserMsgDTO> {

    @Override
    protected void process(User user, UserMsgDTO body) {
        if (!user.hasPermit(Permissions.SPEAK)) {
            user.send(ResponseBuilder.system("您已被禁言！"));
            return;
        }
        if (!Permissions.SPEAK.hasPermit(GlobalConfig.GLOBAL_PERMIT)) {
            user.send(ResponseBuilder.system("鱼塘已开启全员禁言！"));
            return;
        }
        // 游客禁止私聊(无论对端是游客或注册用户)
        if (user.isGuest() && body.getToUsers() != null && body.getToUsers().length > 0) {
            user.send(ResponseBuilder.system("私聊功能已升级，请下载新版客户端并登录账号后使用"));
            return;
        }

        if (body.getMsgType() == UserMsgDTO.MsgType.TEXT) {
            String msg = Convert.toStr(body.getContent());
            if (StrUtil.length(msg) > 200) {
                user.send(ResponseBuilder.system("发送的内容长度不能超过200字符！"));
                return;
            }

            BaiDuFyUtil baiDuFyUtil = Singleton.get(BaiDuFyUtil.class.getName(), () -> new BaiDuFyUtil("", ""));
            body.setContent(baiDuFyUtil.translate(SensitiveWordUtils.loveChina(msg)));
        } else if (body.getMsgType() != UserMsgDTO.MsgType.IMAGE) {
            body.setMsgType(UserMsgDTO.MsgType.TEXT);
        }

        // 公共频道(无 toUsers)消息落库,私聊不存(留 E2EE 阶段)
        // 把落库后的 server id + createdAt 回填给 body,客户端用作 IndexedDB 主键
        if (body.getToUsers() == null || body.getToUsers().length == 0) {
            cn.xeblog.server.history.entity.Message saved = MessageHistoryService.savePublic(user, body);
            body.setServerId(saved.getId());
            body.setServerCreatedAt(saved.getCreatedAt());
        }

        ChannelAction.send(user, body, MessageType.USER);
    }

}
