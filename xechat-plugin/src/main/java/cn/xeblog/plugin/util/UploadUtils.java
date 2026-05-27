package cn.xeblog.plugin.util;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.MessageQuoteDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.entity.react.React;
import cn.xeblog.commons.entity.react.request.UploadReact;
import cn.xeblog.commons.entity.react.result.UploadReactResult;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.InputAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.action.ReactAction;
import cn.xeblog.plugin.action.handler.ReactResultConsumer;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import com.intellij.util.ui.ImageUtil;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author anlingyi
 * @date 2020/9/10
 */
public class UploadUtils {

    private static boolean UPLOADING;
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "apng", "avif", "bmp", "gif", "heic", "heif", "ico", "jfif",
            "jpeg", "jpg", "png", "svg", "tif", "tiff", "webp"
    ));

    public static void uploadImageFile(File file) {
        uploadImageFile(file, null);
    }

    public static void uploadImageFile(File file, String[] toUsers) {
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            inputStream.mark(28);
            String fileType = FileTypeUtil.getType(inputStream);
            if (fileType == null) {
                fileType = getExtension(file.getName());
            }
            String contentType = Files.probeContentType(file.toPath());
            String extension = getExtension(file.getName());
            boolean isImage = contentType != null
                    ? contentType.startsWith("image/")
                    : (fileType != null && IMAGE_EXTENSIONS.contains(fileType))
                    || (extension != null && IMAGE_EXTENSIONS.contains(extension));
            if (!isImage) {
                throw new Exception("不支持的图片类型！");
            }

            inputStream.reset();
            sendImgAsync(IOUtils.toByteArray(inputStream), generateFileName(fileType), toUsers);
        } catch (Exception e) {
            e.printStackTrace();
            ConsoleAction.showSimpleMsg(e.getMessage());
        }
    }

    public static void uploadImage(Image image) {
        uploadImage(image, null);
    }

    public static void uploadImage(Image image, String[] toUsers) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage bufferedImage = ImageUtil.toBufferedImage(image);
            ImageIO.write(bufferedImage, "png", out);
            sendImgAsync(out.toByteArray(), generateFileName("png"), toUsers);
        } catch (IOException e) {
            e.printStackTrace();
            ConsoleAction.showSimpleMsg("图片上传失败！");
        }
    }

    private static String generateFileName(String fileType) {
        fileType = fileType == null ? "jpg" : fileType;
        return IdUtil.fastUUID() + "." + fileType;
    }

    private static String getExtension(String fileName) {
        int idx = fileName == null ? -1 : fileName.lastIndexOf(".");
        if (idx < 0 || idx == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    private static void sendImgAsync(byte[] bytes, String fileName, String[] toUsers) {
        if (UPLOADING) {
            ConsoleAction.showSimpleMsg("请等待之前的图片上传完成！");
            return;
        }

        UPLOADING = true;
        ConsoleAction.showSimpleMsg("图片上传中...");

        UploadReact uploadReact = new UploadReact();
        uploadReact.setFileType(fileName.substring(fileName.lastIndexOf(".") + 1));
        uploadReact.setBytes(bytes);
        uploadReact.setBroadcast(false);
        ReactAction.request(uploadReact, React.UPLOAD, 600, new ReactResultConsumer<UploadReactResult>() {
            @Override
            public void doSucceed(UploadReactResult body) {
                UPLOADING = false;
                MessageQuoteDTO quote = InputAction.currentQuote();
                if (toUsers != null && toUsers.length > 0) {
                    sendPrivateImage(body.getFileName(), toUsers, quote);
                    InputAction.clearQuoteMessage();
                    ConsoleAction.showSimpleMsg("图片上传成功！");
                    return;
                }
                UserMsgDTO dto = new UserMsgDTO(body.getFileName(), UserMsgDTO.MsgType.IMAGE, toUsers);
                dto.setQuote(quote);
                MessageAction.send(dto, Action.CHAT);
                InputAction.clearQuoteMessage();
                ConsoleAction.showSimpleMsg("图片上传成功！");
            }

            @Override
            public void doFailed(String msg) {
                UPLOADING = false;
                ConsoleAction.showSimpleMsg("图片上传失败！原因：" + msg);
            }
        });
    }

    private static void sendPrivateImage(String fileName, String[] toUsers, MessageQuoteDTO quote) {
        if (DataCache.identityPrivKey == null) {
            ConsoleAction.showSimpleMsg("E2EE 私钥未解锁,token 登录无法私聊,请 #exit 后用密码重登");
            return;
        }
        String payload = buildPrivateImagePayload(fileName, quote);
        for (String peerUsername : toUsers) {
            User peer = DataCache.getUser(peerUsername);
            if (peer == null) {
                ConsoleAction.showSimpleMsg("找不到用户: " + peerUsername);
                continue;
            }
            String peerAccount = peer.getAccount();
            if (StrUtil.isBlank(peerAccount)) {
                ConsoleAction.showSimpleMsg(peerUsername + " 是游客,不能私聊");
                continue;
            }
            DataCache.peerAccountByUsername.put(peerUsername, peerAccount);
            E2EESessionService.ensureSessionKey(peerAccount).whenComplete((entry, err) -> {
                if (err != null) {
                    ConsoleAction.showSimpleMsg("E2EE 派生会话密钥失败(" + peerUsername + "): " + err.getMessage());
                    return;
                }
                try {
                    E2EECrypto.EncryptedMessage enc = E2EECrypto.encryptMessage(entry.sessionKey, payload);
                    EncryptedEnvelopeDTO env = new EncryptedEnvelopeDTO();
                    env.setVersion("v1");
                    env.setPeerAccount(peerAccount);
                    env.setPeerAccountId(entry.accountId);
                    env.setIv(enc.iv);
                    env.setCiphertext(enc.ciphertext);
                    MessageAction.send(env, Action.PRIVATE_CHAT);
                } catch (Exception ex) {
                    ConsoleAction.showSimpleMsg("E2EE 加密失败(" + peerUsername + "): " + ex.getMessage());
                }
            });
        }
    }

    private static String buildPrivateImagePayload(String fileName, MessageQuoteDTO quote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", fileName);
        payload.put("msgType", UserMsgDTO.MsgType.IMAGE.name());
        payload.put("originalFileName", null);
        payload.put("quote", quote);
        return JSONUtil.toJsonStr(payload);
    }
}
