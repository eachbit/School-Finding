package com.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "审核请求")
public class ReviewRequest {

    @NotNull(message = "不能为空")
    @Schema(description = "true=通过，false=驳回", example = "true")
    private Boolean pass;

    @Size(max = 200, message = "最多 200 字")
    @Schema(description = "审核意见（驳回时必填）", example = "信息完整，通过")
    private String opinion;
}
