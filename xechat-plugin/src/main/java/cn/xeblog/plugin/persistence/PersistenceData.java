package cn.xeblog.plugin.persistence;

import cn.xeblog.plugin.tools.browser.config.BrowserConfig;
import cn.xeblog.plugin.tools.read.ReadConfig;
import lombok.Data;

import java.util.List;

/**
 * 数据持久化
 *
 * @author anlingyi
 * @date 2022/6/27 5:39 上午
 */
@Data
public class PersistenceData {

    /**
     * 用户名
     */
    private String username;

    /**
     * 消息通知 1.正常通知 2.隐晦通知 3.关闭通知
     */
    private int msgNotify;

    /**
     * 历史命令列表
     */
    private List<String> historyCommandList;

    /**
     * 阅读持久化数据
     */
    private ReadConfig readConfig;

    /**
     * token(账号体系会话凭据)
     */
    private String token;

    /**
     * 上次登录的账号(与 token 配对,启动时若 DataCache.account 与本字段一致才使用 token)
     */
    private String account;

    /**
     * 浏览器配置
     */
    private BrowserConfig browserConfig;

    /**
     * uuid
     */
    private String uuid;

    /**
     * 上次登录用的服务器 host(登录页默认填充用,首次启动为空)
     */
    private String host;

    /**
     * 上次登录用的服务器端口(登录页默认填充用,首次启动为 0)
     */
    private int port;

}
