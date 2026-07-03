package cn.xeblog.server.behavior;

import cn.xeblog.commons.entity.react.result.AdminBehaviorLogDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * player_behavior_logs 表 Mapper。
 *
 * @author dz
 * @date 2026/6/24
 */
public interface PlayerBehaviorLogMapper {

    void insert(PlayerBehaviorLog record);

    long count(@Param("account") String account,
               @Param("nickname") String nickname,
               @Param("ip") String ip,
               @Param("platform") String platform,
               @Param("action") String action,
               @Param("subAction") String subAction,
               @Param("resultStatus") String resultStatus,
               @Param("startAt") Long startAt,
               @Param("endAt") Long endAt);

    List<AdminBehaviorLogDTO> query(@Param("account") String account,
                                    @Param("nickname") String nickname,
                                    @Param("ip") String ip,
                                    @Param("platform") String platform,
                                    @Param("action") String action,
                                    @Param("subAction") String subAction,
                                    @Param("resultStatus") String resultStatus,
                                    @Param("startAt") Long startAt,
                                    @Param("endAt") Long endAt,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

}
