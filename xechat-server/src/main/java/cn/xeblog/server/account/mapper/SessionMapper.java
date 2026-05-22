package cn.xeblog.server.account.mapper;

import cn.xeblog.server.account.entity.SessionEntity;
import org.apache.ibatis.annotations.Param;

/**
 * sessions 表 Mapper
 *
 * @author dz
 * @date 2026/5/22
 */
public interface SessionMapper {

    void insert(SessionEntity session);

    SessionEntity findByToken(@Param("token") String token);

    /**
     * 滑动续期: 更新 last_used_at 和 expires_at
     */
    int touchLastUsed(@Param("token") String token,
                      @Param("lastUsedAt") long lastUsedAt,
                      @Param("expiresAt") long expiresAt);

    int revoke(@Param("token") String token);

    int revokeAllByAccount(@Param("accountId") long accountId);

    /**
     * 清理已过期且已吊销超过 threshold 的记录
     */
    int cleanupExpired(@Param("threshold") long threshold);

}
