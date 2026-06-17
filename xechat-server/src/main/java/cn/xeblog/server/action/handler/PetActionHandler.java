package cn.xeblog.server.action.handler;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.entity.pet.PetResponseDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetService;

@DoAction(Action.PET)
public class PetActionHandler extends AbstractActionHandler<PetRequestDTO> {

    @Override
    protected void process(User user, PetRequestDTO body) {
        if (body == null || body.getPetAction() == null) {
            user.send(ResponseBuilder.build(null, PetResponseDTO.fail(emptyRequest(), "狗狗请求无效"), MessageType.PET));
            return;
        }
        try {
            Object content;
            switch (body.getPetAction()) {
                case PET_PROFILE:
                    content = PetService.profile(user);
                    break;
                case ADOPT:
                    content = PetService.adopt(user, castContent(body.getContent(), PetAdoptDTO.class));
                    break;
                default:
                    user.send(ResponseBuilder.build(null, PetResponseDTO.fail(body, "该狗狗功能还在开发中"), MessageType.PET));
                    return;
            }
            user.send(ResponseBuilder.build(null, PetResponseDTO.ok(body, content), MessageType.PET));
        } catch (Exception e) {
            user.send(ResponseBuilder.build(null, PetResponseDTO.fail(body, e.getMessage()), MessageType.PET));
        }
    }

    private static PetRequestDTO emptyRequest() {
        return new PetRequestDTO();
    }

    private static <T> T castContent(Object raw, Class<T> clazz) {
        if (raw == null) {
            return null;
        }
        if (clazz.isInstance(raw)) {
            return clazz.cast(raw);
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(raw), clazz);
    }
}
