package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.PageResult;
import com.project.common.PageUtil;
import com.project.common.Statuses;
import com.project.dto.ItemRequest;
import com.project.mapper.ItemMapper;
import com.project.mapper.ClaimMapper;
import com.project.pojo.Item;
import com.project.vo.ItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemService {

    private final ItemMapper itemMapper;
    private final LogService logService;
    private final ClaimMapper claimMapper;
    private final UploadService uploadService;

    public ItemService(ItemMapper itemMapper, LogService logService, ClaimMapper claimMapper, UploadService uploadService) {
        this.itemMapper = itemMapper;
        this.logService = logService;
        this.claimMapper = claimMapper;
        this.uploadService = uploadService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemVO create(ItemRequest req) {
        LoginUser me = UserContext.require();
        Item item = new Item();
        copy(req, item);
        item.setPublisherId(me.getId());

        item.setStatus(Statuses.ITEM_PENDING);
        item.setReviewOpinion(null);
        itemMapper.insert(item);
        logService.record(me, Statuses.LOG_ITEM_CREATE, "ITEM", item.getId(), "发布信息，等待管理员审核");
        return itemMapper.selectVOById(item.getId(), true);
    }

    public PageResult<ItemVO> publicList(String type, String category, String location,
                                         String keyword, Integer page, Integer size) {
        int p = PageUtil.page(page);
        int s = PageUtil.size(size);
        long total = itemMapper.countPublic(type, category, location, keyword);
        List<ItemVO> records = total == 0
                ? List.of()
                : itemMapper.selectPublicPage(type, category, location, keyword, PageUtil.offset(p, s), s);
        return PageResult.of(total, p, s, records);
    }

    public ItemVO detail(Long id) {
        LoginUser me = UserContext.require();
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw BizException.of404("信息不存在");
        }
        boolean owner = me.getId().equals(item.getPublisherId());
        boolean admin = Statuses.isAdmin(me.getRole());
        if (!Statuses.ITEM_PUBLIC.equals(item.getStatus()) && !owner && !admin) {

            throw BizException.of404("信息不存在或未公开");
        }

        return itemMapper.selectVOById(id, owner || admin);
    }

    public PageResult<ItemVO> myItems(String status, Integer page, Integer size) {
        LoginUser me = UserContext.require();
        int p = PageUtil.page(page);
        int s = PageUtil.size(size);
        long total = itemMapper.countByPublisher(me.getId(), status);
        List<ItemVO> records = total == 0
                ? List.of()
                : itemMapper.selectByPublisher(me.getId(), status, PageUtil.offset(p, s), s);
        return PageResult.of(total, p, s, records);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemVO close(Long id) {
        LoginUser me = UserContext.require();
        Item item = requireOwn(id, me);
        if (!Statuses.ITEM_PUBLIC.equals(item.getStatus())) {
            throw BizException.of400("只有公开状态的信息可以关闭，当前状态：" + item.getStatus());
        }
        if (claimMapper.selectApprovedByItem(id) != null) {
            throw BizException.of400("该物品存在已通过的认领申请，请先联系管理员撤销后再关闭");
        }
        itemMapper.updateStatus(id, Statuses.ITEM_CLOSED, item.getReviewOpinion());
        int ended = claimMapper.rejectPendingByItem(id, "发布者已关闭信息，申请自动结束", null);
        logService.record(me, Statuses.LOG_ITEM_CLOSE, "ITEM", id, "发布者关闭信息，自动结束待审申请 " + ended + " 条");
        return itemMapper.selectVOById(id, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemVO update(Long id, ItemRequest req) {
        LoginUser me = UserContext.require();
        Item item = requireOwn(id, me);
        if (!Statuses.ITEM_PENDING.equals(item.getStatus()) && !Statuses.ITEM_REJECTED.equals(item.getStatus())) {
            throw BizException.of400("只有待审核或已驳回的信息可以编辑，公开后请先关闭再重新发布");
        }
        copy(req, item);
        item.setStatus(Statuses.ITEM_PENDING);
        item.setReviewOpinion(null);
        itemMapper.updateContent(item);
        logService.record(me, Statuses.LOG_ITEM_UPDATE, "ITEM", id, "编辑信息并重新送审");
        return itemMapper.selectVOById(id, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemVO reopen(Long id) {
        LoginUser me = UserContext.require();
        Item item = requireOwn(id, me);
        if (!Statuses.ITEM_CLOSED.equals(item.getStatus())) {
            throw BizException.of400("只有已关闭的信息可以重新发布，当前状态：" + item.getStatus());
        }
        itemMapper.updateStatus(id, Statuses.ITEM_PENDING, null);
        logService.record(me, Statuses.LOG_ITEM_REOPEN, "ITEM", id, "重新发布并送审");
        return itemMapper.selectVOById(id, true);
    }

    public PageResult<ItemVO> adminList(String status, String type, String keyword, Integer page, Integer size) {
        UserContext.requireAdmin();
        int p = PageUtil.page(page);
        int s = PageUtil.size(size);
        long total = itemMapper.countAdmin(status, type, keyword);
        List<ItemVO> records = total == 0
                ? List.of()
                : itemMapper.selectAdminPage(status, type, keyword, PageUtil.offset(p, s), s);
        return PageResult.of(total, p, s, records);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemVO review(Long id, boolean pass, String opinion) {
        LoginUser admin = UserContext.requireAdmin();
        Item item = itemMapper.selectByIdForUpdate(id);
        if (item == null) {
            throw BizException.of404("信息不存在");
        }
        if (!Statuses.ITEM_PENDING.equals(item.getStatus())) {
            throw BizException.of400("只有待审核的信息可以审核，当前状态：" + item.getStatus());
        }
        if (!pass && (opinion == null || opinion.isBlank())) {
            throw BizException.of400("驳回必须填写审核意见");
        }
        String newStatus = pass ? Statuses.ITEM_PUBLIC : Statuses.ITEM_REJECTED;
        itemMapper.updateStatus(id, newStatus, opinion);
        logService.record(admin, Statuses.LOG_ITEM_REVIEW, "ITEM", id,
                (pass ? "审核通过：" : "审核驳回：") + (opinion == null ? "" : opinion));
        return itemMapper.selectVOById(id, true);
    }

    private Item requireOwn(Long id, LoginUser me) {
        Item item = itemMapper.selectByIdForUpdate(id);
        if (item == null) {
            throw BizException.of404("信息不存在");
        }
        if (!item.getPublisherId().equals(me.getId())) {

            throw BizException.of403("无权限：只能操作自己发布的信息");
        }
        return item;
    }

    private void copy(ItemRequest req, Item item) {
        uploadService.validateOwnership(req.getImageUrl());
        item.setType(req.getType());
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setCategory(req.getCategory());
        item.setLocation(req.getLocation());
        item.setHappenTime(req.getHappenTime());
        item.setColor(req.getColor());
        item.setContact(req.getContact());
        item.setHiddenFeature(req.getHiddenFeature());
        item.setVerifyQuestion(req.getVerifyQuestion());
        item.setVerifyAnswer(req.getVerifyAnswer());
        item.setImageUrl(req.getImageUrl() == null || req.getImageUrl().isBlank() ? null : req.getImageUrl());
    }
}
