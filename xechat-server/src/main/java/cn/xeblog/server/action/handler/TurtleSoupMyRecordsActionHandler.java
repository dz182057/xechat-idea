package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 海龟汤我的记录。
 */
@Slf4j
@DoAction(Action.TURTLE_SOUP_MY_RECORDS)
public class TurtleSoupMyRecordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        try {
            List<TurtleSoupRecordDTO> records = TurtleSoupService.myRecords(user);
            user.send(ResponseBuilder.build(null, records, MessageType.TURTLE_SOUP_RECORDS));
        } catch (Exception e) {
            log.error("查询海龟汤记录异常", e);
            user.send(ResponseBuilder.system("查询海龟汤记录失败"));
        }
    }

}
