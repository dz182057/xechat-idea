package cn.xeblog.commons.entity.pet;

import cn.xeblog.commons.enums.PetAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetResponseDTO {
    private PetAction petAction;
    private Object requestId;
    private boolean success;
    private Object content;
    private String error;

    public static PetResponseDTO ok(PetRequestDTO request, Object content) {
        return new PetResponseDTO(request.getPetAction(), request.getRequestId(), true, content, null);
    }

    public static PetResponseDTO fail(PetRequestDTO request, String error) {
        return new PetResponseDTO(request.getPetAction(), request.getRequestId(), false, null, error);
    }
}
