package cn.xeblog.server.account;

/**
 * 账号体系业务异常,携带中文错误消息。
 *
 * <p>Handler 层捕获后转为客户端 SYSTEM 消息。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
public class AccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountException(String message) {
        super(message);
    }

}
