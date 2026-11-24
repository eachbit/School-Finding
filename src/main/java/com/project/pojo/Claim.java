package com.project.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "认领申请")
public class Claim {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "物品 id")
    private Long itemId;

    @Schema(description = "申请人 id")
    private Long applicantId;

    @Schema(description = "证明文字（敏感，仅本人与管理员可见）")
    private String proof;

    @Schema(description = "对验证问题的回答（敏感）")
    private String answer;

    @Schema(description = "状态：PENDING/APPROVED/REJECTED/CANCELLED/HANDED/REVOKED")
    private String status;

    @Schema(description = "审核意见 / 撤销原因")
    private String reviewOpinion;

    @Schema(description = "审核人 id")
    private Long reviewerId;

    @Schema(description = "交接确认人 id")
    private Long handoverBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
