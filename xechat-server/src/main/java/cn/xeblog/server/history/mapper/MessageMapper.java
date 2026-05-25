package cn.xeblog.server.history.mapper;

import cn.xeblog.server.history.entity.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * messages_public 表 Mapper(公共频道聊天记录)
 *
 * @author dz
 * @date 2026/5/25
 */
public interface MessageMapper {

    void insert(Message message);

    /**
     * 按条件分页查询。
     * <ul>
     *   <li>sinceMs 非 null 时只返回 created_at &gt; sinceMs(增量拉)</li>
     *   <li>beforeId 非 null 时只返回 id &lt; beforeId(向前翻页)</li>
     * </ul>
     * 结果按 id DESC 排,limit+1 条用于判 hasMore。
     */
    List<Message> query(@Param("sinceMs") Long sinceMs,
                        @Param("beforeId") Long beforeId,
                        @Param("limit") int limit);

}
