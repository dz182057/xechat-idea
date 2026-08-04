package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋伙伴视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoPartnerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long accountId;
    private String account;
    private String nickname;
    private Long avatarVersion;
}
