package com.project.web;

import com.project.common.PageResult;
import com.project.common.Result;
import com.project.dto.ReviewRequest;
import com.project.pojo.OpLog;
import com.project.service.AdminService;
import com.project.service.ClaimService;
import com.project.service.ItemService;
import com.project.vo.ClaimVO;
import com.project.vo.ItemVO;
import com.project.vo.StatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "04-管理端", description = "信息审核、认领审核、撤销、交接确认、统计、日志（只有 ADMIN 可调用）")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ItemService itemService;
    private final ClaimService claimService;
    private final AdminService adminService;

    public AdminController(ItemService itemService, ClaimService claimService, AdminService adminService) {
        this.itemService = itemService;
        this.claimService = claimService;
        this.adminService = adminService;
    }

    @Operation(summary = "物品列表（可查待审核；含联系方式、隐藏特征、验证答案）")
    @GetMapping("/items")
    public Result<PageResult<ItemVO>> items(
            @Parameter(description = "PENDING/PUBLIC/REJECTED/CLOSED/CLAIMED") @RequestParam(required = false) String status,
            @Parameter(description = "LOST / FOUND") @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(itemService.adminList(status, type, keyword, page, size));
    }

    @Operation(summary = "审核信息：pass=true 通过（变 PUBLIC），false 驳回（变 REJECTED，必须填意见）")
    @PostMapping("/items/{id}/review")
    public Result<ItemVO> reviewItem(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return Result.ok(itemService.review(id, req.getPass(), req.getOpinion()));
    }

    @Operation(summary = "认领申请列表（status=PENDING 即待审核列表）")
    @GetMapping("/claims")
    public Result<PageResult<ClaimVO>> claims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long itemId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(claimService.adminList(status, itemId, page, size));
    }

    @Operation(summary = "认领详情（含物品隐藏特征与验证参考答案，用于核对证明）")
    @GetMapping("/claims/{id}")
    public Result<ClaimVO> claimDetail(@PathVariable Long id) {
        return Result.ok(claimService.adminDetail(id));
    }

    @Operation(summary = "认领审核：pass=true 通过；同一物品只能有一个通过的申请（行锁保证并发下最多一个成功）")
    @PostMapping("/claims/{id}/review")
    public Result<ClaimVO> reviewClaim(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return Result.ok(claimService.review(id, req.getPass(), req.getOpinion()));
    }

    @Operation(summary = "撤销已通过但未交接的申请")
    @PostMapping("/claims/{id}/revoke")
    public Result<ClaimVO> revoke(@PathVariable Long id, @RequestParam String opinion) {
        return Result.ok(claimService.revoke(id, opinion));
    }

    @Operation(summary = "实际交接确认：申请变 HANDED，物品变 CLAIMED；重复确认不写记录")
    @PostMapping("/claims/{id}/handover")
    public Result<ClaimVO> handover(@PathVariable Long id) {
        return Result.ok(claimService.handover(id));
    }

    @Operation(summary = "3 个统计数字")
    @GetMapping("/statistics")
    public Result<StatisticsVO> statistics() {
        return Result.ok(adminService.statistics());
    }

    @Operation(summary = "最近操作日志")
    @GetMapping("/logs")
    public Result<List<OpLog>> logs(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(adminService.recentLogs(limit));
    }
}
