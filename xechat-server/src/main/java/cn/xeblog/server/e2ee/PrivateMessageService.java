package cn.xeblog.server.e2ee;

import cn.hutool.core.util.IdUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.e2ee.entity.PrivateMessage;
import cn.xeblog.server.e2ee.mapper.PrivateMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 私聊密文持久化与查询。
 *
 * <p>服务端只看密文,不参与加解密;表 messages_private 按
 * (conv_min, conv_max) 双向会话对组织,便于无方向查询。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Slf4j
public final class PrivateMessageService {

    public static final String DEFAULT_VERSION = "v1";

    /**
     * 客户端 limit 上限
     */
    private static final int MAX_LIMIT = 200;

    /**
     * 客户端未指定 limit 时默认
     */
    private static final int DEFAULT_LIMIT = 50;

    private PrivateMessageService() {
    }

    /**
     * 持久化一条私聊密文。返回值带服务端雪花 id + createdAt,供 Handler 回填给客户端。
     */
    public static PrivateMessage save(long senderAccountId, long recipientAccountId,
                                      String iv, String ciphertext, String version) {
        long now = System.currentTimeMillis();
        long min = Math.min(senderAccountId, recipientAccountId);
        long max = Math.max(senderAccountId, recipientAccountId);
        PrivateMessage msg = PrivateMessage.builder()
                .id(IdUtil.getSnowflakeNextId())
                .createdAt(now)
                .senderAccountId(senderAccountId)
                .recipientAccountId(recipientAccountId)
                .convMin(min)
                .convMax(max)
                .iv(iv)
                .ciphertext(ciphertext)
                .version(version == null || version.isEmpty() ? DEFAULT_VERSION : version)
                .build();
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(PrivateMessageMapper.class).insert(msg);
        }
        return msg;
    }

    public static PrivateMessage findById(long id) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(PrivateMessageMapper.class).findById(id);
        }
    }

    public static boolean markRecalled(long id, long recalledAt) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(PrivateMessageMapper.class).markRecalled(id, recalledAt) > 0;
        }
    }

    /**
     * 查与某 peer 的私聊密文历史。返回 (envelopes 按 id 升序, hasMore)。
     */
    public static QueryResult queryHistory(long meAccountId, long peerAccountId,
                                           Long sinceMs, Long beforeId, int limit) {
        int effective = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        long min = Math.min(meAccountId, peerAccountId);
        long max = Math.max(meAccountId, peerAccountId);

        List<PrivateMessage> rows;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            rows = session.getMapper(PrivateMessageMapper.class)
                    .query(min, max, sinceMs, beforeId, effective + 1);
        }

        boolean hasMore = rows.size() > effective;
        if (hasMore) {
            rows = rows.subList(0, effective);
        }
        // SQL 是 id DESC,客户端期望老→新
        Collections.reverse(rows);

        List<EncryptedEnvelopeDTO> envelopes = new ArrayList<>(rows.size());
        for (PrivateMessage m : rows) {
            envelopes.add(toEnvelope(m, meAccountId));
        }
        return new QueryResult(envelopes, hasMore);
    }

    /**
     * 把 PrivateMessage 转成 EncryptedEnvelopeDTO,peerAccountId 总是"对方"(与 me 相对),
     * senderAccountId 始终是这条消息的实际发送方(客户端用它判 isSelf 决定气泡方向)。
     * 注:peerAccount 字段(账号字符串)在 service 层留空,由调用方根据 accounts 表回填。
     */
    private static EncryptedEnvelopeDTO toEnvelope(PrivateMessage m, long meAccountId) {
        long peerId = (m.getSenderAccountId() == meAccountId)
                ? m.getRecipientAccountId() : m.getSenderAccountId();
        return new EncryptedEnvelopeDTO(
                m.getVersion(),
                null,
                peerId,
                m.getIv(),
                m.getCiphertext(),
                m.getId(),
                m.getCreatedAt(),
                m.getSenderAccountId(),
                m.getRecalledAt() != null);
    }

    /**
     * 历史查询结果
     */
    public static final class QueryResult {
        public final List<EncryptedEnvelopeDTO> envelopes;
        public final boolean hasMore;

        public QueryResult(List<EncryptedEnvelopeDTO> envelopes, boolean hasMore) {
            this.envelopes = envelopes;
            this.hasMore = hasMore;
        }
    }

}
