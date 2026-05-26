package cn.xeblog.server.e2ee.mapper;

import cn.xeblog.server.e2ee.entity.PrivateMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * messages_private 表 Mapper
 *
 * @author dz
 * @date 2026/5/26
 */
public interface PrivateMessageMapper {

    void insert(PrivateMessage msg);

    /**
     * 按会话对(convMin, convMax)查密文历史;过滤 sinceMs / beforeId,id DESC,limit+1 用于判 hasMore。
     */
    List<PrivateMessage> query(@Param("convMin") long convMin,
                               @Param("convMax") long convMax,
                               @Param("sinceMs") Long sinceMs,
                               @Param("beforeId") Long beforeId,
                               @Param("limit") int limit);

}
