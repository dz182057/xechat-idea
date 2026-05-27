package cn.xeblog.plugin.util;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.IdUtil;
import cn.xeblog.commons.entity.react.React;
import cn.xeblog.commons.entity.react.request.UploadReact;
import cn.xeblog.commons.entity.react.result.UploadReactResult;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.ReactAction;
import cn.xeblog.plugin.action.handler.ReactResultConsumer;
import com.intellij.util.ui.ImageUtil;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
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
            sendImgAsync(IOUtils.toByteArray(inputStream), generateFileName(fileType));
        } catch (Exception e) {
            e.printStackTrace();
            ConsoleAction.showSimpleMsg(e.getMessage());
        }
    }

    public static void uploadImage(Image image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage bufferedImage = ImageUtil.toBufferedImage(image);
            ImageIO.write(bufferedImage, "png", out);
            sendImgAsync(out.toByteArray(), generateFileName("png"));
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

    private static void sendImgAsync(byte[] bytes, String fileName) {
        if (UPLOADING) {
            ConsoleAction.showSimpleMsg("请等待之前的图片上传完成！");
            return;
        }

        UPLOADING = true;
        ConsoleAction.showSimpleMsg("图片上传中...");

        UploadReact uploadReact = new UploadReact();
        uploadReact.setFileType(fileName.substring(fileName.lastIndexOf(".") + 1));
        uploadReact.setBytes(bytes);
        ReactAction.request(uploadReact, React.UPLOAD, 600, new ReactResultConsumer<UploadReactResult>() {
            @Override
            public void doSucceed(UploadReactResult body) {
                UPLOADING = false;
                ConsoleAction.showSimpleMsg("图片上传成功！");
            }

            @Override
            public void doFailed(String msg) {
                UPLOADING = false;
                ConsoleAction.showSimpleMsg("图片上传失败！原因：" + msg);
            }
        });
    }
}
