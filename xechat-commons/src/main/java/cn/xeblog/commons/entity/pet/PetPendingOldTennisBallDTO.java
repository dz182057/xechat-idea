package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 待处理的旧网球彩蛋。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetPendingOldTennisBallDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dogId;

    private String dogName;

}
