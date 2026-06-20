package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 旧网球彩蛋选择请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetResolveOldTennisBallDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * return：扔回去；collect：收藏。
     */
    private String choice;

}
