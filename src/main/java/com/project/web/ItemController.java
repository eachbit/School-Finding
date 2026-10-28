package com.project.web;

import com.project.common.PageResult;
import com.project.common.Result;
import com.project.dto.ItemRequest;
import com.project.service.ItemService;
import com.project.service.MatchService;
import com.project.vo.ItemVO;
import com.project.vo.MatchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "02-失物招领信息", description = "发布、公开列表、详情、我的发布、关闭、匹配推荐")
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;
    private final MatchService matchService;

    public ItemController(ItemService itemService, MatchService matchService) {
        this.itemService = itemService;
        this.matchService = matchService;
    }

    @Operation(summary = "发布失物/拾物（发布后状态为 PENDING，需管理员审核）")
    @PostMapping("/items")
    public Result<ItemVO> create(@Valid @RequestBody ItemRequest req) {
        return Result.ok(itemService.create(req));
    }

    @Operation(summary = "公开列表（只含已审核公开的信息，支持类别/地点/关键词筛选）")
    @GetMapping("/items")
    public Result<PageResult<ItemVO>> list(
            @Parameter(description = "LOST / FOUND，不传则全部") @RequestParam(required = false) String type,
            @Parameter(description = "类别，精确匹配") @RequestParam(required = false) String category,
            @Parameter(description = "地点，模糊匹配") @RequestParam(required = false) String location,
            @Parameter(description = "关键词，匹配标题或描述") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，最大 50") @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(itemService.publicList(type, category, location, keyword, page, size));
    }

    @Operation(summary = "详情（不含联系方式/隐藏特征/答案；返回验证问题供认领者作答）")
    @GetMapping("/items/{id}")
    public Result<ItemVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id));
    }

    @Operation(summary = "匹配推荐（失物找拾物，反之亦然；≥60 分取前 5 条）")
    @GetMapping("/items/{id}/matches")
    public Result<List<MatchVO>> matches(@PathVariable Long id) {
        return Result.ok(matchService.matches(id));
    }

    @Operation(summary = "编辑并重新送审（仅待审核/已驳回）")
    @PutMapping("/items/{id}")
    public Result<ItemVO> update(@PathVariable Long id, @Valid @RequestBody ItemRequest req) {
        return Result.ok(itemService.update(id, req));
    }

    @Operation(summary = "关闭我发布的信息（仅公开状态）")
    @PostMapping("/items/{id}/close")
    public Result<ItemVO> close(@PathVariable Long id) {
        return Result.ok(itemService.close(id));
    }

    @Operation(summary = "关闭后重新发布（重新进入待审核）")
    @PostMapping("/items/{id}/reopen")
    public Result<ItemVO> reopen(@PathVariable Long id) {
        return Result.ok(itemService.reopen(id));
    }

    @Operation(summary = "我的发布（可按状态筛选）")
    @GetMapping("/me/items")
    public Result<PageResult<ItemVO>> myItems(
            @Parameter(description = "PENDING/PUBLIC/REJECTED/CLOSED/CLAIMED") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(itemService.myItems(status, page, size));
    }
}
