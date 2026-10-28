package com.project.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "物品")
public class Item {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "类型：LOST=失物，FOUND=拾物")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "类别")
    private String category;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "丢失/拾取时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenTime;

    @Schema(description = "颜色（可空）")
    private String color;

    @Schema(description = "联系方式（敏感）")
    private String contact;

    @Schema(description = "隐藏特征（敏感，仅管理员可见）")
    private String hiddenFeature;

    @Schema(description = "认领验证问题")
    private String verifyQuestion;

    @Schema(description = "验证参考答案（敏感，仅管理员可见）")
    private String verifyAnswer;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "发布者 id")
    private Long publisherId;

    @Schema(description = "审核意见")
    private String reviewOpinion;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
