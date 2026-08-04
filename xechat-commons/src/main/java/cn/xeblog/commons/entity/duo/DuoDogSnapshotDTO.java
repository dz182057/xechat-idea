package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋内最小化狗狗快照，不包含个人资产。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoDogSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long ownerAccountId;
    private String dogId;
    private String name;
    private String breed;
    private String stage;
    private int bond;
}
