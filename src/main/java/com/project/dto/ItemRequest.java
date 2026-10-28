package com.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "发布/编辑物品请求")
public class ItemRequest {

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "LOST|FOUND", message = "只能是 LOST 或 FOUND")
    @Schema(description = "类型：LOST=失物，FOUND=拾物", example = "FOUND")
    private String type;

    @NotBlank(message = "不能为空")
    @Size(max = 100, message = "最多 100 字")
    @Schema(description = "标题", example = "白色充电宝")
    private String title;

    @NotBlank(message = "不能为空")
    @Size(max = 1000, message = "最多 1000 字")
    @Schema(description = "描述", example = "第三教学楼 302 教室捡到的白色充电宝，已交到值班室")
    private String description;

    @NotBlank(message = "不能为空")
    @Schema(description = "类别", example = "电子设备")
    private String category;

    @NotBlank(message = "不能为空")
    @Schema(description = "地点", example = "第三教学楼302教室")
    private String location;

    @NotNull(message = "不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "丢失/拾取时间", example = "2026-10-22 09:30:00")
    private LocalDateTime happenTime;

    @Schema(description = "颜色（可不填，不填则不参与判断）", example = "白色")
    private String color;

    @NotBlank(message = "不能为空")
    @Schema(description = "联系方式（不会出现在公开列表）", example = "13800000002")
    private String contact;

    @Schema(description = "隐藏特征（仅管理员可见，用于核验认领人）", example = "背面贴了小熊贴纸")
    private String hiddenFeature;

    @Schema(description = "认领验证问题（会展示给认领者）", example = "充电宝背面贴了什么？")
    private String verifyQuestion;

    @Schema(description = "验证参考答案（仅管理员可见）", example = "小熊贴纸")
    private String verifyAnswer;
}
