package com.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "不能为空")
    @Size(min = 4, max = 20, message = "长度需为 4~20")
    @Schema(description = "登录名", example = "student3")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度需为 6~32")
    @Schema(description = "密码（明文提交，后端 BCrypt 哈希后入库）", example = "123456")
    private String password;

    @Schema(description = "联系方式", example = "13800000003")
    private String contact;
}
