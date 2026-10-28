package com.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "匹配推荐结果")
public class MatchVO {

    private Long itemId;

    private String title;

    private String type;

    private String category;

    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenTime;

    private String color;

    @Schema(description = "规则相似度总分（0~100），不是找回概率")
    private int score;

    @Schema(description = "得分理由，逐条说明怎么加的分")
    private List<String> reasons;
}
