package com.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "认领申请视图")
public class ClaimVO {

    private Long id;

    private Long itemId;

    @Schema(description = "物品标题")
    private String itemTitle;

    @Schema(description = "物品类型")
    private String itemType;

    @Schema(description = "物品当前状态")
    private String itemStatus;

    private Long applicantId;

    @Schema(description = "申请人用户名")
    private String applicantName;

    @Schema(description = "证明文字")
    private String proof;

    @Schema(description = "验证问题回答")
    private String answer;

    private String status;

    @Schema(description = "审核意见 / 撤销原因")
    private String reviewOpinion;

    private Long reviewerId;

    @Schema(description = "审核人用户名")
    private String reviewerName;

    private Long handoverBy;

    @Schema(description = "交接确认人用户名")
    private String handoverByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "物品隐藏特征（仅管理员可见）")
    private String itemHiddenFeature;

    @Schema(description = "验证问题（仅管理员可见）")
    private String itemVerifyQuestion;

    @Schema(description = "验证参考答案（仅管理员可见）")
    private String itemVerifyAnswer;
}
