package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋邀请视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoInviteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long invitedByAccountId;
    private long expiresAt;
}
