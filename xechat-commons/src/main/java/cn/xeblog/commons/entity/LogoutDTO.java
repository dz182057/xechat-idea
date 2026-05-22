package cn.xeblog.commons.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * LOGOUT 请求(空 body 占位 DTO,框架要求 body 非空,客户端发 {})
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
public class LogoutDTO implements Serializable {

    private static final long serialVersionUID = 1L;

}
