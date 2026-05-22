package cn.xeblog.server.account.mapper;

import cn.xeblog.server.account.entity.InviteCode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * invite_codes 表 Mapper
 *
 * @author dz
 * @date 2026/5/22
 */
public interface InviteCodeMapper {

    void insert(InviteCode inviteCode);

    InviteCode findByCode(@Param("code") String code);

    /**
     * 累加使用次数 + 回填使用者
     */
    int incrementUsed(@Param("code") String code,
                      @Param("usedBy") long usedBy,
                      @Param("usedAt") long usedAt);

    /**
     * @param includeUsed true=返回全部(含已用满/吊销), false=仅未用满且未吊销
     */
    List<InviteCode> listAll(@Param("includeUsed") boolean includeUsed);

    int revoke(@Param("code") String code);

}
