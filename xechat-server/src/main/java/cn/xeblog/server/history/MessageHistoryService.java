package cn.xeblog.server.history;

import cn.hutool.core.util.IdUtil;
import cn.xeblog.commons.entity.HistoryMsgDTO;
import cn.xeblog.commons.entity.MessageQuoteDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import cn.hutool.json.JSONUtil;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.history.entity.Message;
import cn.xeblog.server.history.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 公共频道聊天记录持久化与查询服务。
 *
 * <p>写入: {@link #savePublic(User, UserMsgDTO)} 在 ChatActionHandler 处理公共消息时调用。
 * 查询: {@link #queryPublic(Long, Long, int)} 由 PullHistoryActionHandler 调用。</p>
 *
 * <p>注:私聊消息暂不落库,留待 E2EE 阶段统一加密存储。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Slf4j
public final class MessageHistoryService {

    /**
     * 客户端 limit 上限,防止恶意请求一次拉太多
     */
    private static final int MAX_LIMIT = 200;

    /**
     * 客户端未指定 limit 时默认
     */
    private static final int DEFAULT_LIMIT = 50;

    /**
     * Response.time 的格式与 Response 默认构造器一致
     */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private MessageHistoryService() {
    }

    /**
     * 把一条公共频道消息落库。
     * 调用方应已确认 toUsers 为空(公共频道)。
     */
    public static Message savePublic(User sender, UserMsgDTO body) {
        long now = System.currentTimeMillis();
        Message msg = Message.builder()
                .id(IdUtil.getSnowflakeNextId())
                .createdAt(now)
                .senderAccountId(sender.isGuest() ? null : sender.getAccountId())
                .senderGuestUuid(sender.isGuest() ? sender.getUuid() : null)
                .senderNickname(sender.getNickname())
                .msgType(body.getMsgType() == null ? Message.MSG_TYPE_TEXT : body.getMsgType().name())
                .content(serializeContent(body.getContent()))
                .quoteJson(body.getQuote() == null ? null : JSONUtil.toJsonStr(body.getQuote()))
                .build();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(MessageMapper.class).insert(msg);
        } catch (Exception e) {
            // 落库失败不阻塞消息广播,降级为只发不存
            log.error("公共消息落库失败 sender={} contentLen={}",
                    sender.getNickname(),
                    msg.getContent().length(), e);
        }
        return msg;
    }

    private static String serializeContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof CharSequence) {
            return String.valueOf(content);
        }
        return JSONUtil.toJsonStr(content);
    }

    public static Message findPublic(long id) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(MessageMapper.class).findById(id);
        }
    }

    public static boolean markPublicRecalled(long id, long recalledAt) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(MessageMapper.class).markRecalled(id, recalledAt) > 0;
        }
    }

    /**
     * 拉取公共频道历史。返回的 HistoryMsgDTO.msgList 按 id 升序(老→新)。
     *
     * @param sinceMs   仅返回 created_at &gt; sinceMs(增量拉取);null 表示不限
     * @param beforeId  仅返回 id &lt; beforeId(向前翻页);null 表示不限
     * @param limit     0 / 负 = 默认 50,超 MAX_LIMIT 时截到 MAX_LIMIT
     */
    public static HistoryMsgDTO queryPublic(Long sinceMs, Long beforeId, int limit) {
        int effective = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // +1 用于判 hasMore
        List<Message> rows;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            rows = session.getMapper(MessageMapper.class)
                    .query(sinceMs, beforeId, effective + 1);
        }

        boolean hasMore = rows.size() > effective;
        if (hasMore) {
            rows = rows.subList(0, effective);
        }

        // SQL 按 id DESC,前端要老→新,反转一下
        Collections.reverse(rows);

        List<Response> msgList = new ArrayList<>(rows.size());
        for (Message m : rows) {
            msgList.add(toResponse(m));
        }
        return new HistoryMsgDTO(msgList, hasMore);
    }

    /**
     * 把 Message 还原成一条 Response&lt;UserMsgDTO&gt;,客户端可直接复用现有 chat 消息渲染。
     *
     * <p>注:历史消息的 sender 只保留 accountId/guest uuid/nickname/isGuest,
     * 不带 channel/region/avatarVersion 等运行时字段(避免历史展示有错)。</p>
     */
    private static Response toResponse(Message m) {
        User sender = new User();
        sender.setNickname(m.getSenderNickname());
        sender.setUsername(m.getSenderNickname());
        sender.setStatus(UserStatus.FISHING);
        sender.setPlatform(Platform.IDEA);
        if (m.getSenderAccountId() != null) {
            sender.setAccountId(m.getSenderAccountId());
        } else {
            sender.setGuest(true);
            sender.setUuid(m.getSenderGuestUuid());
        }

        UserMsgDTO body = new UserMsgDTO();
        body.setContent(m.getContent());
        if (m.getQuoteJson() != null && !m.getQuoteJson().isEmpty()) {
            try {
                body.setQuote(JSONUtil.toBean(m.getQuoteJson(), MessageQuoteDTO.class));
            } catch (Exception e) {
                log.warn("公共历史引用解析失败 messageId={}", m.getId(), e);
            }
        }
        body.setServerId(m.getId());
        body.setServerCreatedAt(m.getCreatedAt());
        body.setRecalled(m.getRecalledAt() != null);
        try {
            body.setMsgType(UserMsgDTO.MsgType.valueOf(m.getMsgType()));
        } catch (Exception e) {
            body.setMsgType(UserMsgDTO.MsgType.TEXT);
        }

        Response resp = new Response(sender, body, MessageType.USER);
        // 用消息原始时间格式化,而不是 now
        String formatted = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(m.getCreatedAt()),
                ZoneId.systemDefault()
        ).format(TIME_FMT);
        resp.setTime(formatted);
        return resp;
    }

}
