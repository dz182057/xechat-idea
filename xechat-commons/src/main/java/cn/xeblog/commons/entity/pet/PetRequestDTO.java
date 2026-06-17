package cn.xeblog.commons.entity.pet;

import cn.xeblog.commons.enums.PetAction;
import lombok.Data;

@Data
public class PetRequestDTO {
    private PetAction petAction;
    private Object requestId;
    private Object content;
}
