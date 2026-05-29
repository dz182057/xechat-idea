package cn.xeblog.server.account;

import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.account.entity.LoginLog;
import cn.xeblog.server.account.mapper.LoginLogMapper;
import cn.xeblog.server.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

/**
 * 登录记录服务。
 *
 * @author dz
 * @date 2026/5/29
 */
@Slf4j
public final class LoginLogService {

    private LoginLogService() {
    }

    public static void record(Long accountId, String ip, Platform platform,
                              boolean success, String failReason) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(LoginLogMapper.class).insert(LoginLog.builder()
                    .accountId(accountId)
                    .ip(ip)
                    .region(IpUtil.getRegionStrByIp(ip))
                    .platform(platform == null ? null : platform.name())
                    .success(success)
                    .failReason(success ? null : failReason)
                    .createdAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("写入登录记录失败 accountId={} success={}: {}", accountId, success, e.getMessage());
        }
    }

}
