package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DELETE_ACCOUNT 请求(注销自己的账号,需二次输密码)
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String password;

}
