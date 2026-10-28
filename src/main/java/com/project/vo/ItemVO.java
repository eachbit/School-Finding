package com.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "物品视图")
public class ItemVO {

    private Long id;

    @Schema(description = "类型：LOST=失物，FOUND=拾物")
    private String type;

    private String title;

    private String description;

    private String category;

    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenTime;

    private String color;

    @Schema(description = "状态")
    private String status;

    private Long publisherId;

    @Schema(description = "发布者用户名")
    private String publisherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "审核意见（发布者本人 / 管理员可见）")
    private String reviewOpinion;

    @Schema(description = "联系方式（仅本人 / 管理员可见）")
    private String contact;

    @Schema(description = "隐藏特征（仅管理员可见）")
    private String hiddenFeature;

    @Schema(description = "认领验证问题（详情里对认领者展示）")
    private String verifyQuestion;

    @Schema(description = "验证参考答案（仅管理员可见）")
    private String verifyAnswer;
}
