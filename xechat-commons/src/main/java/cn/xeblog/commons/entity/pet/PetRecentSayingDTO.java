package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 最近已读的狗狗问候动态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetRecentSayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignmentId;
    private String petName;
    private String category;
    private String primaryText;
    private Long readAt;

}
