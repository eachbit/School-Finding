package com.project.web;

import com.project.common.Result;
import com.project.dto.ClaimRequest;
import com.project.service.ClaimService;
import com.project.vo.ClaimVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "03-认领申请（学生）", description = "提交认领、我的申请、取消申请")
@RestController
@RequestMapping("/api")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Operation(summary = "提交认领申请（需填写证明文字 + 验证问题答案）")
    @PostMapping("/items/{id}/claims")
    public Result<ClaimVO> submit(@PathVariable Long id, @Valid @RequestBody ClaimRequest req) {
        return Result.ok(claimService.submit(id, req));
    }

    @Operation(summary = "我的申请列表")
    @GetMapping("/me/claims")
    public Result<List<ClaimVO>> myClaims() {
        return Result.ok(claimService.myClaims());
    }

    @Operation(summary = "取消待审核的申请")
    @PostMapping("/claims/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        claimService.cancel(id);
        return Result.ok();
    }
}
