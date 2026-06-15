package cn.xeblog.commons.entity.pet;

import cn.xeblog.commons.enums.PetAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙请求壳。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetAction petAction;

    private Long requestId;

    private Object content;

}
