package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 查询对端身份公钥请求(GET_PEER_KEY)。
 *
 * <p>客户端首次与某 peer 私聊时调用,服务端返回 {@link PeerKeyResponseDTO}。
 * 注:peer 必须是注册账号(游客无身份密钥)。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetPeerKeyDTO implements Serializable {

    /**
     * 对端账号(account 字段,不是 nickname,精确匹配)
     */
    private String account;

}
