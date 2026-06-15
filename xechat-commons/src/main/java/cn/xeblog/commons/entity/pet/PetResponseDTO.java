package cn.xeblog.commons.entity.pet;

import cn.xeblog.commons.enums.PetAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙响应壳。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetAction petAction;

    private Long requestId;

    private boolean success;

    private Object content;

    private String error;

    public static PetResponseDTO ok(PetAction petAction, Object content) {
        return ok(petAction, null, content);
    }

    public static PetResponseDTO ok(PetAction petAction, Long requestId, Object content) {
        return new PetResponseDTO(petAction, requestId, true, content, null);
    }

    public static PetResponseDTO fail(PetAction petAction, String error) {
        return fail(petAction, null, error);
    }

    public static PetResponseDTO fail(PetAction petAction, Long requestId, String error) {
        return new PetResponseDTO(petAction, requestId, false, null, error);
    }

}
