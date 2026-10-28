package com.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "token，后续请求放在 Authorization 头里")
    private String token;

    private Long userId;

    private String username;

    @Schema(description = "角色：STUDENT / ADMIN")
    private String role;

    private String contact;
}
