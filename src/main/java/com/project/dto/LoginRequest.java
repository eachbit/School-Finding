package com.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "最多 50 字")
    @Schema(description = "登录名", example = "student1")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(max = 72, message = "密码过长")
    @Schema(description = "密码", example = "123456")
    private String password;
}
