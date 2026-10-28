package com.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端统计")
public class StatisticsVO {

    @Schema(description = "公开拾物总数（type=FOUND 且 status=PUBLIC）")
    private long publicFoundCount;

    @Schema(description = "已领取拾物数（type=FOUND 且 status=CLAIMED）")
    private long claimedFoundCount;

    @Schema(description = "待审核认领申请数（claim.status=PENDING）")
    private long pendingClaimCount;
}
