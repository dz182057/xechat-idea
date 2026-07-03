package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.react.result.AdminPetResourceOverviewDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理员狗狗之家资源概览 Mapper。
 */
public interface AdminPetResourceOverviewMapper {

    long count(@Param("account") String account,
               @Param("nickname") String nickname);

    List<AdminPetResourceOverviewDTO> query(@Param("account") String account,
                                            @Param("nickname") String nickname,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

}
