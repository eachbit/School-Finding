package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.PageResult;
import com.project.common.PageUtil;
import com.project.common.Statuses;
import com.project.dto.ClaimRequest;
import com.project.mapper.ClaimMapper;
import com.project.mapper.ItemMapper;
import com.project.pojo.Claim;
import com.project.pojo.Item;
import com.project.vo.ClaimVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClaimService {

    private final ClaimMapper claimMapper;
    private final ItemMapper itemMapper;
    private final LogService logService;

    public ClaimService(ClaimMapper claimMapper, ItemMapper itemMapper, LogService logService) {
        this.claimMapper = claimMapper;
        this.itemMapper = itemMapper;
        this.logService = logService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimVO submit(Long itemId, ClaimRequest req) {
        LoginUser me = UserContext.require();
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BizException.of404("信息不存在");
        }
        if (item.getPublisherId().equals(me.getId())) {
            throw BizException.of400("不能认领自己发布的信息");
        }

        if (!Statuses.ITEM_PUBLIC.equals(item.getStatus())) {
            throw BizException.of400("该信息当前不可认领，状态：" + item.getStatus());
        }

        Claim exist = claimMapper.selectValidByItemAndApplicant(itemId, me.getId());
        if (exist != null) {
            throw BizException.of400("你对该物品已有进行中的申请（id=" + exist.getId() + "，状态=" + exist.getStatus()
                    + "），请等待处理或先取消");
        }

        Claim claim = new Claim();
        claim.setItemId(itemId);
        claim.setApplicantId(me.getId());
        claim.setProof(req.getProof());
        claim.setAnswer(req.getAnswer());
        claim.setStatus(Statuses.CLAIM_PENDING);
        claimMapper.insert(claim);

        logService.record(me, Statuses.LOG_CLAIM_SUBMIT, "CLAIM", claim.getId(), "申请人提交认领，物品 id=" + itemId);

        ClaimVO vo = claimMapper.selectVOById(claim.getId());
        vo.setItemHiddenFeature(null);
        vo.setItemVerifyAnswer(null);
        return vo;
    }

    public List<ClaimVO> myClaims() {
        LoginUser me = UserContext.require();

        return claimMapper.selectByApplicant(me.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long claimId) {
        LoginUser me = UserContext.require();
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw BizException.of404("申请不存在");
        }
        if (!claim.getApplicantId().equals(me.getId())) {
            throw BizException.of403("无权限：只能取消自己的申请");
        }
        if (!Statuses.CLAIM_PENDING.equals(claim.getStatus())) {
            throw BizException.of400("只有待审核的申请可以取消，当前状态：" + claim.getStatus());
        }
        claimMapper.updateStatus(claimId, Statuses.CLAIM_CANCELLED, "申请人主动取消");
        logService.record(me, Statuses.LOG_CLAIM_CANCEL, "CLAIM", claimId, "申请人取消申请");
    }

    public PageResult<ClaimVO> adminList(String status, Long itemId, Integer page, Integer size) {
        int p = PageUtil.page(page);
        int s = PageUtil.size(size);
        long total = claimMapper.countAdmin(status, itemId);
        List<ClaimVO> records = total == 0
                ? List.of()
                : claimMapper.selectAdminPage(status, itemId, PageUtil.offset(p, s), s);
        return PageResult.of(total, p, s, records);
    }

    public ClaimVO adminDetail(Long claimId) {
        ClaimVO vo = claimMapper.selectVOById(claimId);
        if (vo == null) {
            throw BizException.of404("申请不存在");
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimVO review(Long claimId, boolean pass, String opinion) {
        LoginUser admin = UserContext.require();

        Claim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null) {
            throw BizException.of404("申请不存在");
        }
        if (!Statuses.CLAIM_PENDING.equals(claim.getStatus())) {
            throw BizException.of400("该申请已被处理（当前状态：" + claim.getStatus() + "），不能重复审核");
        }

        Item item = itemMapper.selectByIdForUpdate(claim.getItemId());
        if (item == null) {
            throw BizException.of404("物品不存在");
        }

        if (pass) {
            if (Statuses.ITEM_CLAIMED.equals(item.getStatus())) {
                throw BizException.of400("该物品已被领取，不能再通过其它申请");
            }
            if (!Statuses.ITEM_PUBLIC.equals(item.getStatus())) {
                throw BizException.of400("该物品当前不可通过申请，状态：" + item.getStatus());
            }

            Claim approved = claimMapper.selectApprovedByItem(item.getId());
            if (approved != null && !approved.getId().equals(claimId)) {
                throw BizException.of400("该物品已有通过的申请（申请 id=" + approved.getId()
                        + "，状态=" + approved.getStatus() + "），不能再通过新的申请");
            }
        } else if (opinion == null || opinion.isBlank()) {
            throw BizException.of400("驳回必须填写审核意见");
        }

        claimMapper.updateReview(claimId, pass ? Statuses.CLAIM_APPROVED : Statuses.CLAIM_REJECTED,
                opinion, admin.getId());
        logService.record(admin, Statuses.LOG_CLAIM_REVIEW, "CLAIM", claimId,
                (pass ? "通过：" : "驳回：") + (opinion == null ? "" : opinion));
        return claimMapper.selectVOById(claimId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimVO revoke(Long claimId, String opinion) {
        LoginUser admin = UserContext.require();
        Claim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null) {
            throw BizException.of404("申请不存在");
        }
        if (!Statuses.CLAIM_APPROVED.equals(claim.getStatus())) {
            throw BizException.of400("只有审核通过且未交接的申请可以撤销，当前状态：" + claim.getStatus());
        }
        if (opinion == null || opinion.isBlank()) {
            throw BizException.of400("撤销必须填写原因");
        }
        claimMapper.updateStatus(claimId, Statuses.CLAIM_REVOKED, "管理员撤销：" + opinion);
        logService.record(admin, Statuses.LOG_CLAIM_REVOKE, "CLAIM", claimId, "撤销已通过的申请：" + opinion);
        return claimMapper.selectVOById(claimId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimVO handover(Long claimId) {
        LoginUser admin = UserContext.require();
        Claim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null) {
            throw BizException.of404("申请不存在");
        }

        if (Statuses.CLAIM_HANDED.equals(claim.getStatus())) {
            throw BizException.of400("该申请已完成交接，无需重复确认（未新增交接记录）");
        }
        if (!Statuses.CLAIM_APPROVED.equals(claim.getStatus())) {
            throw BizException.of400("只有审核通过的申请才能确认交接，当前状态：" + claim.getStatus());
        }

        Item item = itemMapper.selectByIdForUpdate(claim.getItemId());
        if (item == null) {
            throw BizException.of404("物品不存在");
        }
        if (Statuses.ITEM_CLAIMED.equals(item.getStatus())) {
            throw BizException.of400("该物品已领取，不能重复交接");
        }

        claimMapper.updateHandover(claimId, Statuses.CLAIM_HANDED, admin.getId());
        itemMapper.updateStatus(item.getId(), Statuses.ITEM_CLAIMED, item.getReviewOpinion());
        logService.record(admin, Statuses.LOG_CLAIM_HANDOVER, "CLAIM", claimId,
                "交接确认：物品 id=" + item.getId() + " 已交给申请人 id=" + claim.getApplicantId());
        return claimMapper.selectVOById(claimId);
    }
}
