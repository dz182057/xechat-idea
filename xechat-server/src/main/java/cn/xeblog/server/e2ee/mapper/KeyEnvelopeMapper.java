package cn.xeblog.server.e2ee.mapper;

import cn.xeblog.server.e2ee.entity.KeyEnvelope;
import org.apache.ibatis.annotations.Param;

/**
 * key_envelopes 表 Mapper
 *
 * @author dz
 * @date 2026/5/26
 */
public interface KeyEnvelopeMapper {

    /** UPSERT 风格:存在则覆盖 envelope + updatedAt */
    int upsert(KeyEnvelope envelope);

    KeyEnvelope find(@Param("accountId") long accountId,
                     @Param("type") String type);

}
