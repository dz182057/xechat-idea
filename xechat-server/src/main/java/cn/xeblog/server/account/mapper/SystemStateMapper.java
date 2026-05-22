package cn.xeblog.server.account.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * system_state 单值 key-value 表 Mapper
 *
 * <p>用法:存放 pending_initial_admin 标记等单例状态</p>
 *
 * @author dz
 * @date 2026/5/22
 */
public interface SystemStateMapper {

    String get(@Param("key") String key);

    void set(@Param("key") String key, @Param("value") String value);

    void delete(@Param("key") String key);

}
