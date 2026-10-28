package com.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "提交认领申请")
public class ClaimRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 1000, message = "最多 1000 字")
    @Schema(description = "证明文字", example = "充电宝外壳贴了小熊贴纸，背面还有一条浅划痕")
    private String proof;

    @Size(max = 100, message = "最多 100 字")
    @Schema(description = "对验证问题的回答", example = "卡通贴纸")
    private String answer;
}
