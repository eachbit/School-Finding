package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import com.project.dto.ClaimRequest;
import com.project.dto.ItemRequest;
import com.project.mapper.ClaimMapper;
import com.project.mapper.ItemMapper;
import com.project.pojo.Claim;
import com.project.pojo.Item;
import com.project.vo.ClaimVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowValidationTest {
    private final ItemMapper items = mock(ItemMapper.class);
    private final ClaimMapper claims = mock(ClaimMapper.class);
    private final LogService logs = mock(LogService.class);
    private final ClaimService service = new ClaimService(claims, items, logs);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void cannotSubmitWithoutAnswerToQuestion() {
        UserContext.set(new LoginUser(1L, "student", Statuses.ROLE_STUDENT));
        Item item = publicItem();
        item.setVerifyQuestion("伞柄有什么痕迹？");
        when(items.selectById(2L)).thenReturn(item);
        when(items.selectByIdForUpdate(2L)).thenReturn(item);
        when(claims.selectVOById(any())).thenReturn(new ClaimVO());
        ClaimRequest req = new ClaimRequest();
        req.setProof("我拥有该物品的证明");
        assertThrows(BizException.class, () -> service.submit(2L, req));
        verify(claims, never()).insert(any());
    }

    @Test
    void cannotHandoverClosedItem() {
        UserContext.set(new LoginUser(3L, "admin", Statuses.ROLE_ADMIN));
        Claim claim = new Claim();
        claim.setId(8L);
        claim.setItemId(2L);
        claim.setStatus(Statuses.CLAIM_APPROVED);
        Item item = publicItem();
        item.setStatus(Statuses.ITEM_CLOSED);
        when(claims.selectById(8L)).thenReturn(claim);
        when(claims.selectByIdForUpdate(8L)).thenReturn(claim);
        when(items.selectByIdForUpdate(2L)).thenReturn(item);
        assertThrows(BizException.class, () -> service.handover(8L));
        verify(claims, never()).updateHandover(anyLong(), any(), anyLong());
    }

    @Test
    void databaseSizedFieldsRejectOversizedInput() {
        ItemRequest req = new ItemRequest();
        req.setType("FOUND");
        req.setTitle("雨伞");
        req.setDescription("图书馆捡到");
        req.setCategory("雨".repeat(33));
        req.setLocation("图书馆");
        req.setHappenTime(java.time.LocalDateTime.now());
        req.setContact("13800000000");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(req).isEmpty());
        }
    }

    @Test
    void studentCannotReviewClaimViaService() {
        UserContext.set(new LoginUser(1L, "student", Statuses.ROLE_STUDENT));
        Claim claim = new Claim();
        claim.setId(8L);
        claim.setItemId(2L);
        claim.setStatus(Statuses.CLAIM_PENDING);
        when(claims.selectById(8L)).thenReturn(claim);
        when(claims.selectByIdForUpdate(8L)).thenReturn(claim);
        when(items.selectByIdForUpdate(2L)).thenReturn(publicItem());
        assertThrows(BizException.class, () -> service.review(8L, true, "通过"));
        verify(claims, never()).updateReview(anyLong(), any(), any(), anyLong());
    }

    private Item publicItem() {
        Item item = new Item();
        item.setId(2L);
        item.setPublisherId(5L);
        item.setStatus(Statuses.ITEM_PUBLIC);
        return item;
    }
}
