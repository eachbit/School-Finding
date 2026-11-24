package com.project.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "操作日志")
public class OpLog {

    private Long id;

    @Schema(description = "操作人 id")
    private Long operatorId;

    @Schema(description = "操作人用户名")
    private String operatorName;

    @Schema(description = "操作类型")
    private String action;

    @Schema(description = "目标类型：ITEM / CLAIM")
    private String targetType;

    @Schema(description = "目标 id")
    private Long targetId;

    @Schema(description = "意见 / 说明")
    private String opinion;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
