package cn.xeblog.server.behavior;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Request;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.entity.react.request.AdminReact;
import cn.xeblog.commons.entity.react.result.AdminBehaviorLogDTO;
import cn.xeblog.commons.entity.react.result.AdminReactResult;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 玩家行为流水服务。
 *
 * <p>行为记录不能影响主业务；写入异常只打警告。</p>
 *
 * @author dz
 * @date 2026/6/24
 */
@Slf4j
public final class PlayerBehaviorLogService {

    private static final int MAX_BODY_JSON_LENGTH = 8000;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String MASKED_VALUE = "[已脱敏]";
    private static final Pattern SENSITIVE_JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|oldPassword|newPassword|token|accessToken|refreshToken|"
                    + "identityPrivKeyEnvelope|newIdentityPrivKeyEnvelope|privateKey|secret|"
                    + "avatarBase64|base64)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

    private PlayerBehaviorLogService() {
    }

    public static void record(User user, Request<?> request, String resultStatus, String errorMessage) {
        record(user, user == null ? null : user.getIp(), request, resultStatus, errorMessage);
    }

    public static void record(User user, String ip, Request<?> request, String resultStatus, String errorMessage) {
        if (request == null || request.getAction() == null) {
            return;
        }

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            String requestBodyJson = toJson(request.getBody());
            session.getMapper(PlayerBehaviorLogMapper.class).insert(PlayerBehaviorLog.builder()
                    .accountId(user == null || user.getAccountId() <= 0L ? null : user.getAccountId())
                    .account(user == null ? null : trimToNull(user.getAccount()))
                    .nickname(user == null ? null : trimToNull(user.getNickname()))
                    .guest(user != null && user.isGuest())
                    .platform(user == null || user.getPlatform() == null ? null : user.getPlatform().name())
                    .clientUuid(user == null ? null : trimToNull(user.getUuid()))
                    .ip(trimToNull(ip))
                    .region(StrUtil.isBlank(ip) ? null : IpUtil.getRegionStrByIp(ip))
                    .action(request.getAction().name())
                    .subAction(resolveSubAction(request))
                    .protocol(request.getProtocol() == null ? null : request.getProtocol().name())
                    .resultStatus(trimToNull(resultStatus))
                    .errorMessage(limit(trimToNull(errorMessage), MAX_ERROR_MESSAGE_LENGTH))
                    .requestBodyJson(requestBodyJson)
                    .createdAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("写入玩家行为记录失败 action={} accountId={}: {}",
                    request.getAction(), user == null ? null : user.getAccountId(), e.getMessage());
        }
    }

    public static AdminReactResult query(AdminReact body) {
        int page = body.getPage() == null || body.getPage() < 1 ? 1 : body.getPage();
        int pageSize = body.getPageSize() == null || body.getPageSize() < 1 ? 20 : body.getPageSize();
        pageSize = Math.min(pageSize, 100);
        int offset = (page - 1) * pageSize;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PlayerBehaviorLogMapper mapper = session.getMapper(PlayerBehaviorLogMapper.class);
            String account = trimToNull(body.getAccount());
            String nickname = trimToNull(body.getNickname());
            String ip = trimToNull(body.getIp());
            String platform = trimToNull(body.getPlatform());
            String action = trimToNull(body.getAction());
            String subAction = trimToNull(body.getSubAction());
            String resultStatus = trimToNull(body.getResultStatus());
            Long startAt = body.getStartAt();
            Long endAt = body.getEndAt();
            long total = mapper.count(account, nickname, ip, platform, action, subAction, resultStatus, startAt, endAt);
            List<AdminBehaviorLogDTO> records = mapper.query(
                    account, nickname, ip, platform, action, subAction, resultStatus, startAt, endAt, offset, pageSize
            );
            return new AdminReactResult(records, total, page, pageSize);
        }
    }

    private static String resolveSubAction(Request<?> request) {
        if (request.getAction() != Action.PET) {
            return null;
        }
        Object body = request.getBody();
        if (body == null) {
            return null;
        }
        try {
            PetRequestDTO petRequest = body instanceof PetRequestDTO
                    ? (PetRequestDTO) body
                    : JSONUtil.toBean(JSONUtil.toJsonStr(body), PetRequestDTO.class);
            return petRequest.getPetAction() == null ? null : petRequest.getPetAction().name();
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return limit(maskSensitiveJsonFields(JSONUtil.toJsonStr(value)), MAX_BODY_JSON_LENGTH);
        } catch (Exception e) {
            return limit(String.valueOf(value), MAX_BODY_JSON_LENGTH);
        }
    }

    private static String maskSensitiveJsonFields(String json) {
        if (json == null) {
            return null;
        }
        Matcher matcher = SENSITIVE_JSON_FIELD_PATTERN.matcher(json);
        StringBuffer masked = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(matcher.group(1) + "\"" + MASKED_VALUE + "\""));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
