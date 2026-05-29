package cn.xeblog.server.account;

import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.entity.react.request.AdminReact;
import cn.xeblog.commons.entity.react.result.AdminLoginLogDTO;
import cn.xeblog.commons.entity.react.result.AdminReactResult;
import cn.xeblog.server.account.entity.LoginLog;
import cn.xeblog.server.account.mapper.LoginLogMapper;
import cn.xeblog.server.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

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

    public static AdminReactResult query(AdminReact body) {
        int page = body.getPage() == null || body.getPage() < 1 ? 1 : body.getPage();
        int pageSize = body.getPageSize() == null || body.getPageSize() < 1 ? 20 : body.getPageSize();
        pageSize = Math.min(pageSize, 100);
        int offset = (page - 1) * pageSize;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            LoginLogMapper mapper = session.getMapper(LoginLogMapper.class);
            String account = trimToNull(body.getAccount());
            String nickname = trimToNull(body.getNickname());
            String ip = trimToNull(body.getIp());
            String platform = trimToNull(body.getPlatform());
            Long startAt = body.getStartAt();
            Long endAt = body.getEndAt();
            Boolean success = body.getSuccess();
            long total = mapper.count(account, nickname, ip, platform, success, startAt, endAt);
            List<AdminLoginLogDTO> records = mapper.query(
                    account, nickname, ip, platform, success, startAt, endAt, offset, pageSize
            );
            return new AdminReactResult(records, total, page, pageSize);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
