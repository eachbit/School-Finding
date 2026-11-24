package com.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileRequest {
    @NotNull(message = "请提供联系方式，可填空字符串清除")
    @Size(max = 100, message = "最多 100 字")
    private String contact;
}
