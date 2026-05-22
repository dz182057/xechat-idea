package cn.xeblog.server.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.server.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 头像服务:base64 上传裁剪 + 默认头像生成 + 文件读取。
 *
 * <p>路径: {@link GlobalConfig#AVATAR_DIR}/{accountId}.png</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
public final class AvatarService {

    /**
     * 头像最终尺寸
     */
    private static final int AVATAR_SIZE = 256;

    /**
     * 上传 base64 解码后的最大字节数(256 KB)
     */
    private static final int MAX_DECODED_BYTES = 256 * 1024;

    /**
     * 默认头像调色板(12 种)
     */
    private static final Color[] PALETTE = {
            new Color(0xEF4444), new Color(0xF97316), new Color(0xEAB308),
            new Color(0x84CC16), new Color(0x22C55E), new Color(0x14B8A6),
            new Color(0x06B6D4), new Color(0x3B82F6), new Color(0x6366F1),
            new Color(0x8B5CF6), new Color(0xA855F7), new Color(0xEC4899)
    };

    /**
     * 默认头像缓存,key=nickname。容量限制,溢出后新条目不缓存
     */
    private static final ConcurrentHashMap<String, byte[]> DEFAULT_CACHE = new ConcurrentHashMap<>();
    private static final int DEFAULT_CACHE_MAX = 256;

    private AvatarService() {
    }

    /**
     * 保存上传的头像(base64),裁剪到 256x256 PNG。
     *
     * @return AvatarService 写完后新版本号
     * @throws AccountException base64 非法/解码后体积过大/图片解码失败
     */
    public static int saveAvatar(long accountId, String base64) {
        if (StrUtil.isBlank(base64)) {
            throw new AccountException("头像数据不能为空");
        }
        // 容忍 data:image/...;base64, 前缀
        int commaIdx = base64.indexOf(',');
        String pure = commaIdx >= 0 ? base64.substring(commaIdx + 1) : base64;

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(pure);
        } catch (IllegalArgumentException e) {
            throw new AccountException("头像 base64 解码失败");
        }
        if (decoded.length > MAX_DECODED_BYTES) {
            throw new AccountException("头像文件过大(限 256KB)");
        }

        Path target = Paths.get(GlobalConfig.AVATAR_DIR, accountId + ".png");
        try {
            Files.createDirectories(target.getParent());
            // thumbnailator: 等比缩放裁剪到 256x256,输出 PNG
            try (ByteArrayInputStream in = new ByteArrayInputStream(decoded)) {
                Thumbnails.of(in)
                        .size(AVATAR_SIZE, AVATAR_SIZE)
                        .crop(net.coobird.thumbnailator.geometry.Positions.CENTER)
                        .outputFormat("png")
                        .toFile(target.toFile());
            }
        } catch (IOException e) {
            log.error("保存头像失败 accountId={}", accountId, e);
            throw new AccountException("头像保存失败,请重试");
        }

        int newVersion = AccountService.incrementAvatarVersion(accountId);
        log.info("头像更新 accountId={} version={}", accountId, newVersion);
        return newVersion;
    }

    /**
     * 读取已上传头像;若文件不存在则返回 null,调用方决定是否走 {@link #getOrGenerateDefault}。
     */
    public static byte[] readAvatar(long accountId) {
        Path target = Paths.get(GlobalConfig.AVATAR_DIR, accountId + ".png");
        if (!Files.exists(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.warn("读取头像失败 accountId={} err={}", accountId, e.getMessage());
            return null;
        }
    }

    /**
     * 生成或返回缓存的默认头像(色块 + 首字母)。
     */
    public static byte[] getOrGenerateDefault(String nickname) {
        String key = nickname == null || nickname.isEmpty() ? "?" : nickname;
        byte[] cached = DEFAULT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = generateDefault(key);
        if (DEFAULT_CACHE.size() < DEFAULT_CACHE_MAX) {
            DEFAULT_CACHE.put(key, bytes);
        }
        return bytes;
    }

    private static byte[] generateDefault(String nickname) {
        Color bg = PALETTE[Math.floorMod(nickname.hashCode(), PALETTE.length)];
        String letter = nickname.substring(0, 1).toUpperCase();

        BufferedImage img = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(bg);
            g.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE);

            g.setColor(Color.WHITE);
            // SansSerif 是 JVM 逻辑字体,中英都能 fallback
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 128);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(letter);
            int textAscent = fm.getAscent();
            int textHeight = fm.getHeight();
            int x = (AVATAR_SIZE - textWidth) / 2;
            int y = (AVATAR_SIZE - textHeight) / 2 + textAscent;
            g.drawString(letter, x, y);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成默认头像失败", e);
        }
    }

}
