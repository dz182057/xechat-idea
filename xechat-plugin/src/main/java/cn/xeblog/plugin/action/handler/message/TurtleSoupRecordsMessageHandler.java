package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupRecordDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.turtlesoup.TurtleSoup;

import java.util.List;

/**
 * 海龟汤记录。
 */
@DoMessage(MessageType.TURTLE_SOUP_RECORDS)
public class TurtleSoupRecordsMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof TurtleSoup)) {
            return;
        }
        List<TurtleSoupRecordDTO> records = JSONUtil.parseArray(JSONUtil.toJsonStr(response.getBody()))
                .toList(TurtleSoupRecordDTO.class);
        ((TurtleSoup) action).onRecords(records);
    }

}
