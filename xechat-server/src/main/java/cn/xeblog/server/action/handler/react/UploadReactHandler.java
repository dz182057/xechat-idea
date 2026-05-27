package cn.xeblog.server.action.handler.react;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.entity.react.React;
import cn.xeblog.commons.entity.react.request.UploadReact;
import cn.xeblog.commons.entity.react.result.ReactResult;
import cn.xeblog.commons.entity.react.result.UploadReactResult;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Permissions;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.annotation.DoReact;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.history.MessageHistoryService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;

/**
 * @author anlingyi
 * @date 2022/9/19 8:53 AM
 */
@Slf4j
@DoReact(React.UPLOAD)
public class UploadReactHandler extends AbstractReactHandler<UploadReact, UploadReactResult> {

    @Override
    protected void process(User user, UploadReact body, ReactResult<UploadReactResult> result) {
        UploadReactResult data = saveImage(user, body.getFileType(), body.getBytes(), result);
        if (data == null) {
            return;
        }

        if (Boolean.FALSE.equals(body.getBroadcast())) {
            return;
        }

        UserMsgDTO dto = new UserMsgDTO();
        dto.setMsgType(UserMsgDTO.MsgType.IMAGE);
        dto.setContent(data.getFileName());
        cn.xeblog.server.history.entity.Message saved = MessageHistoryService.savePublic(user, dto);
        dto.setServerId(saved.getId());
        dto.setServerCreatedAt(saved.getCreatedAt());
        ChannelAction.send(user, dto, MessageType.USER);
    }

    public static UploadReactResult saveImage(User user, String fileType, byte[] bytes, ReactResult<UploadReactResult> result) {
        if (!user.hasPermit(Permissions.SEND_FILE)) {
            result.setMsg("您没有上传文件的权限！");
            return null;
        }
        if (!Permissions.SEND_FILE.hasPermit(GlobalConfig.GLOBAL_PERMIT)) {
            result.setMsg("鱼塘已禁止发送文件！");
            return null;
        }

        int maxSize = GlobalConfig.UPLOAD_FILE_MAX_SIZE;

        int len = ArrayUtil.length(bytes);
        if (len > maxSize * 1024) {
            result.setMsg("发送的文件大小不能超过" + maxSize + "KB!");
            return null;
        }

        String filePath = GlobalConfig.UPLOAD_FILE_PATH;
        String filename = IdUtil.fastUUID() + "." + normalizeFileType(fileType);
        File imageFile = new File(filePath + "/" + filename);
        if (!imageFile.exists()) {
            FileUtil.mkdir(filePath);
            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                out.write(bytes);

                UploadReactResult data = new UploadReactResult();
                data.setFileName(filename);
                result.setData(data);
                result.setSucceed(true);
                return data;
            } catch (Exception e) {
                log.error("文件上传异常", e);
                result.setMsg("文件上传失败！");
            }
        }
        return null;
    }

    private static String normalizeFileType(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            return "png";
        }
        String clean = fileType.trim().toLowerCase();
        clean = clean.replaceAll("[^a-z0-9]", "");
        return clean.isEmpty() ? "png" : clean;
    }

}
