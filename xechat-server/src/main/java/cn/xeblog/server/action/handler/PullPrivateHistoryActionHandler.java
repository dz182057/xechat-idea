package cn.xeblog.server.action.handler;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.PullPrivateHistoryDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.e2ee.E2EEKeyService;
import cn.xeblog.server.e2ee.PrivateMessageService;
import lombok.extern.slf4j.Slf4j;

/**
 * 拉与某 peer 的私聊密文历史(PULL_PRIVATE_HISTORY)。
 *
 * <p>客户端首次进入私聊或上滚到顶时调用,支持增量(sinceMs)与翻页(beforeId)。
 * 服务端按 (min, max) 双向会话对查 messages_private,本地解密后展示。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Slf4j
@DoAction(Action.PULL_PRIVATE_HISTORY)
public class PullPrivateHistoryActionHandler extends AbstractActionHandler<PullPrivateHistoryDTO> {

    @Override
    protected void process(User user, PullPrivateHistoryDTO body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不支持私聊历史"));
            return;
        }
        if (body == null || StrUtil.isBlank(body.getPeerAccount())) {
            user.send(ResponseBuilder.system("拉取私聊历史需要 peerAccount"));
            return;
        }

        try {
            Account peer = E2EEKeyService.requirePeerWithPubKey(body.getPeerAccount());
            int limit = body.getLimit() == null ? 0 : body.getLimit();
            PrivateMessageService.QueryResult result = PrivateMessageService.queryHistory(
                    user.getAccountId(), peer.getAccountId(),
                    body.getSinceMs(), body.getBeforeId(), limit);

            // service 层 peerAccount 字段空着,这里统一回填(整批 peer 都同一个)
            for (EncryptedEnvelopeDTO env : result.envelopes) {
                env.setPeerAccount(peer.getAccount());
            }

            PullPrivateHistoryDTO resp = new PullPrivateHistoryDTO();
            resp.setPeerAccount(peer.getAccount());
            resp.setSinceMs(body.getSinceMs());
            resp.setBeforeId(body.getBeforeId());
            resp.setLimit(body.getLimit());
            resp.setEnvelopes(result.envelopes);
            resp.setHasMore(result.hasMore);
            user.send(ResponseBuilder.build(null, resp, MessageType.PRIVATE_HISTORY));
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception ex) {
            log.error("拉私聊历史异常 me={} peer={} since={} before={} limit={}",
                    user.getAccount(), body.getPeerAccount(),
                    body.getSinceMs(), body.getBeforeId(), body.getLimit(), ex);
            user.send(ResponseBuilder.system("拉取私聊历史失败,请稍后重试"));
        }
    }

}
