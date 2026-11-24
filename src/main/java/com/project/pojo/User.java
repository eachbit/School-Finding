package com.project.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户")
public class User {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @JsonIgnore
    private String password;

    @Schema(description = "角色：STUDENT / ADMIN")
    private String role;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
