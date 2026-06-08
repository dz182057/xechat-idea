package cn.xeblog.server.config;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Permissions;

import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author anlingyi
 * @date 2023/2/17 9:19 PM
 */
public class GlobalConfig {

    /**
     * 数据根目录系统属性名。
     */
    public static final String DATA_PATH_PROPERTY = "xechat.data.path";

    /**
     * 数据根目录。
     */
    public static String DATA_PATH;

    /**
     * 上传的文件路径
     */
    public static String UPLOAD_FILE_PATH;

    /**
     * 账号体系数据目录(SQLite db + 头像)
     */
    public static String DATA_DIR;

    /**
     * SQLite 数据库文件路径
     */
    public static String DB_PATH;

    /**
     * 头像目录
     */
    public static String AVATAR_DIR;

    static {
        initDataPath(null);
    }

    /**
     * 初始化数据根目录。未配置时保持原有 user.home/xechat 行为。
     *
     * @param configuredDataPath 配置文件或命令行指定的数据根目录
     */
    public static synchronized void initDataPath(String configuredDataPath) {
        String dataPath = StrUtil.blankToDefault(configuredDataPath, System.getProperty(DATA_PATH_PROPERTY));
        dataPath = StrUtil.blankToDefault(dataPath, System.getProperty("user.home") + "/xechat");
        DATA_PATH = Paths.get(dataPath).normalize().toString();
        UPLOAD_FILE_PATH = Paths.get(DATA_PATH, "upload").toString();
        DATA_DIR = Paths.get(DATA_PATH, "data").toString();
        DB_PATH = Paths.get(DATA_DIR, "xechat.db").toString();
        AVATAR_DIR = Paths.get(DATA_DIR, "avatars").toString();
    }

    /**
     * 上传的文件大小最大值，单位：KB
     */
    public static int UPLOAD_FILE_MAX_SIZE = 2 << 10;

    /**
     * 全局权限
     */
    public static int GLOBAL_PERMIT = Permissions.ALL.getValue();

    /**
     * 用户权限缓存
     */
    public static final Map<String, Integer> USER_PERMIT_CACHE = new ConcurrentHashMap<>(32);

    /**
     * 获取用户权限
     *
     * @param user
     * @return
     */
    public static int getUserPermit(User user) {
        int permit = Permissions.ALL.getValue();

        if (user == null) {
            return permit;
        }

        String uuid = user.getUuid();
        String ip = user.getIp();

        if (StrUtil.isNotBlank(user.getUuid())) {
            Integer permitByUuid  = USER_PERMIT_CACHE.get(uuid);
            if (permitByUuid != null) {
                return permitByUuid;
            }
        }

        if (StrUtil.isNotBlank(user.getIp())) {
            Integer permitByIp = USER_PERMIT_CACHE.get(ip);
            if (permitByIp != null) {
                return permitByIp;
            }
        }

        return permit;
    }

    /**
     * 添加用户权限
     *
     * @param user
     * @param permit
     */
    public static void addUserPermit(User user, int permit) {
        String uuid = user.getUuid();
        String ip = user.getIp();

        if (StrUtil.isNotBlank(uuid)) {
            USER_PERMIT_CACHE.put(uuid, permit);
        }
        if (StrUtil.isNotBlank(ip)) {
            USER_PERMIT_CACHE.put(ip, permit);
        }
    }

}
