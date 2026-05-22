package cn.xeblog.server.account.mapper;

import cn.xeblog.server.account.entity.Account;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * accounts 表 Mapper
 *
 * @author dz
 * @date 2026/5/22
 */
public interface AccountMapper {

    void insert(Account account);

    Account findByAccount(@Param("account") String account);

    Account findByNickname(@Param("nickname") String nickname);

    Account findById(@Param("accountId") long accountId);

    int updateNickname(@Param("accountId") long accountId,
                       @Param("nickname") String nickname);

    int updatePassword(@Param("accountId") long accountId,
                       @Param("passwordHash") String passwordHash);

    int updateRole(@Param("accountId") long accountId,
                   @Param("role") String role);

    int incrementAvatarVersion(@Param("accountId") long accountId);

    int softDelete(@Param("accountId") long accountId,
                   @Param("deletedAt") long deletedAt);

    int updateLastLogin(@Param("accountId") long accountId,
                        @Param("lastLoginAt") long lastLoginAt,
                        @Param("lastLoginIp") String lastLoginIp);

    long countAll();

    List<Account> findByIdIn(@Param("ids") Collection<Long> ids);

}
