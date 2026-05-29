package cn.xeblog.server.account.mapper;

import cn.xeblog.commons.entity.react.result.AdminLoginLogDTO;
import cn.xeblog.server.account.entity.LoginLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * login_logs 表 Mapper。
 *
 * @author dz
 * @date 2026/5/29
 */
public interface LoginLogMapper {

    void insert(LoginLog loginLog);

    long count(@Param("account") String account,
               @Param("nickname") String nickname,
               @Param("ip") String ip,
               @Param("platform") String platform,
               @Param("success") Boolean success,
               @Param("startAt") Long startAt,
               @Param("endAt") Long endAt);

    List<AdminLoginLogDTO> query(@Param("account") String account,
                                 @Param("nickname") String nickname,
                                 @Param("ip") String ip,
                                 @Param("platform") String platform,
                                 @Param("success") Boolean success,
                                 @Param("startAt") Long startAt,
                                 @Param("endAt") Long endAt,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

}
