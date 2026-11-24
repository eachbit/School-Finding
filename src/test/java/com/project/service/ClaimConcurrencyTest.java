package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import com.project.dto.ClaimRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ClaimConcurrencyTest {

    private long publisherId;
    private long applicantA;
    private long applicantB;
    private long admin1;
    private long admin2;
    private final List<Long> userIds = new ArrayList<>();

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long itemId;
    private Long claimA;
    private Long claimB;

    @Test
    @DisplayName("同一人并发提交同一物品，只产生一个有效申请")
    void concurrentSubmission_createsOneValidClaim() throws Exception {
        jdbc.update("DELETE FROM claim WHERE id = ?", claimA);
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    UserContext.set(new LoginUser(applicantA, "applicant", Statuses.ROLE_STUDENT));
                    try {
                        startLine.await();
                        ClaimRequest req = new ClaimRequest();
                        req.setProof("并发提交所有权证明");
                        req.setAnswer("白色划痕");
                        claimService.submit(itemId, req);
                        return "OK";
                    } catch (BizException e) {
                        return "REJECTED";
                    } finally {
                        UserContext.clear();
                    }
                }));
            }
            startLine.countDown();
            long succeeded = 0;
            for (Future<String> result : results) {
                if ("OK".equals(result.get(20, TimeUnit.SECONDS))) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM claim WHERE item_id = ? AND applicant_id = ? AND status IN ('PENDING','APPROVED','HANDED')",
                    Integer.class, itemId, applicantA));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("取消与审批同时发生时只有一种状态变更成功")
    void concurrentCancelAndReview_cannotOverwriteEachOther() throws Exception {
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> review = pool.submit(reviewTask(admin1, claimA, startLine));
            Future<String> cancel = pool.submit(() -> {
                UserContext.set(new LoginUser(applicantA, "applicant", Statuses.ROLE_STUDENT));
                try {
                    startLine.await();
                    claimService.cancel(claimA);
                    return "OK";
                } catch (BizException e) {
                    return "FAIL";
                } finally {
                    UserContext.clear();
                }
            });
            startLine.countDown();
            List<String> outcomes = List.of(review.get(15, TimeUnit.SECONDS), cancel.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("OK"::equals).count());
            assertEquals(1, countLogs(claimA));
            String status = jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimA);
            assertTrue(List.of("APPROVED", "CANCELLED").contains(status));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("两名管理员并发交接只成功一次且只写一条交接日志")
    void concurrentHandover_recordsOneHandover() throws Exception {
        UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
        claimService.review(claimA, true, "证明一致");
        UserContext.clear();
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> handover = () -> {
                UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
                try {
                    startLine.await();
                    claimService.handover(claimA);
                    return "OK";
                } catch (BizException e) {
                    return "FAIL";
                } finally {
                    UserContext.clear();
                }
            };
            Future<String> first = pool.submit(handover);
            Future<String> second = pool.submit(handover);
            startLine.countDown();
            assertEquals(1, List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)).stream().filter("OK"::equals).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM op_log WHERE action = 'CLAIM_HANDOVER' AND target_id = ?", Integer.class, claimA));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("有已通过申请的物品不能关闭，交接仍可继续")
    void approvedClaim_preventsClosingItem() {
        UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
        claimService.review(claimA, true, "核对一致");
        UserContext.set(new LoginUser(publisherId, "student2", Statuses.ROLE_STUDENT));
        try {
            assertThrows(BizException.class, () -> itemService.close(itemId));
            assertEquals("PUBLIC", jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, itemId));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("物品关闭后待审申请自动结束，重投后可重新申请")
    void closingItem_endsPendingClaims() {
        UserContext.set(new LoginUser(publisherId, "student2", Statuses.ROLE_STUDENT));
        try {
            itemService.close(itemId);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM claim WHERE item_id = ? AND status = 'PENDING'", Integer.class, itemId));
            itemService.reopen(itemId);
            UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
            itemService.review(itemId, true, "重新公开");
            UserContext.set(new LoginUser(applicantA, "student1", Statuses.ROLE_STUDENT));
            ClaimRequest req = new ClaimRequest();
            req.setProof("重新提交的证明");
            req.setAnswer("白色划痕");
            assertEquals("PENDING", claimService.submit(itemId, req).getStatus());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("完成交接后其它申请自动驳回")
    void handover_rejectsOtherPendingClaims() {
        UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
        try {
            claimService.review(claimA, true, "核对一致");
            assertEquals("PUBLIC", jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, itemId));
            claimService.handover(claimA);
            assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimB));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("有验证问题时空回答不能提交")
    void question_requiresAnswer() {
        jdbc.update("DELETE FROM claim WHERE id = ?", claimA);
        UserContext.set(new LoginUser(applicantA, "student1", Statuses.ROLE_STUDENT));
        ClaimRequest req = new ClaimRequest();
        req.setProof("充足的物品所有权证明");
        try {
            assertThrows(BizException.class, () -> claimService.submit(itemId, req));
        } finally {
            UserContext.clear();
        }
    }

    @BeforeEach
    void setUp() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).endsWith("_test"),
                "集成测试必须连接独立的 _test 数据库");
        publisherId = insertUser(Statuses.ROLE_STUDENT);
        applicantA = insertUser(Statuses.ROLE_STUDENT);
        applicantB = insertUser(Statuses.ROLE_STUDENT);
        admin1 = insertUser(Statuses.ROLE_ADMIN);
        admin2 = insertUser(Statuses.ROLE_ADMIN);

        jdbc.update("INSERT INTO item(type, title, description, category, location, happen_time, color, contact, "
                + "hidden_feature, verify_question, verify_answer, status, publisher_id, created_at, updated_at) "
                + "VALUES ('FOUND', '【并发测试】黑色雨伞', '并发测试数据', '雨具', '图书馆一楼大厅', NOW(), '黑色', '138', "
                + "'伞柄有白色划痕', '伞柄有什么痕迹？', '白色划痕', 'PUBLIC', ?, NOW(), NOW())", publisherId);
        itemId = jdbc.queryForObject("SELECT id FROM item WHERE publisher_id = ? ORDER BY id DESC LIMIT 1", Long.class, publisherId);

        claimA = insertClaim(itemId, applicantA);
        claimB = insertClaim(itemId, applicantB);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    @Test
    @DisplayName("两名管理员同时审批同一物品的不同申请，最多一个成功")
    void concurrentReview_onlyOneSucceeds() throws Exception {
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> taskA = reviewTask(admin1, claimA, startLine);
            Callable<String> taskB = reviewTask(admin2, claimB, startLine);

            Future<String> fa = pool.submit(taskA);
            Future<String> fb = pool.submit(taskB);
            startLine.countDown();

            String ra = fa.get(15, TimeUnit.SECONDS);
            String rb = fb.get(15, TimeUnit.SECONDS);

            System.out.println("[并发审批] 管理员1 -> " + ra);
            System.out.println("[并发审批] 管理员2 -> " + rb);

            long success = java.util.Arrays.stream(new String[]{ra, rb}).filter("OK"::equals).count();
            assertEquals(1, success, "同一物品的两个申请，只能有一个审批通过成功");

            Integer approved = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM claim WHERE item_id = ? AND status IN ('APPROVED','HANDED')",
                    Integer.class, itemId);
            assertEquals(1, approved, "同一物品只能有一个通过的申请");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("重复交接确认不会重复写交接记录和日志")
    void repeatHandover_noDuplicateRecord() {
        UserContext.set(new LoginUser(admin1, "admin", Statuses.ROLE_ADMIN));
        try {

            claimService.review(claimA, true, "证明一致，通过");

            int logsBefore = countLogs(claimA);
            claimService.handover(claimA);

            String claimStatus = jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimA);
            String itemStatus = jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, itemId);
            assertEquals("HANDED", claimStatus, "交接后申请状态应为 HANDED");
            assertEquals("CLAIMED", itemStatus, "交接后物品状态应为 CLAIMED");

            int logsAfterFirst = countLogs(claimA);
            assertEquals(logsBefore + 1, logsAfterFirst, "第一次交接应写 1 条日志");

            assertThrows(BizException.class, () -> claimService.handover(claimA));
            assertEquals(logsAfterFirst, countLogs(claimA), "重复交接不得新增交接记录/日志");

            Integer handed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM claim WHERE item_id = ? AND status = 'HANDED'", Integer.class, itemId);
            assertEquals(1, handed, "同一物品只能有一条 HANDED 记录");

            List<String> actions = jdbc.queryForList(
                    "SELECT action FROM op_log WHERE target_type = 'CLAIM' AND target_id = ? ORDER BY id",
                    String.class, claimA);
            assertTrue(actions.contains("CLAIM_HANDOVER"), "日志里应有交接记录");
            assertEquals(1, actions.stream().filter("CLAIM_HANDOVER"::equals).count(), "交接日志只应有一条");
        } finally {
            UserContext.clear();
        }
    }

    private Callable<String> reviewTask(long adminId, Long claimId, CountDownLatch startLine) {
        String adminName = adminId == admin1 ? "admin" : "admin2";
        return () -> {
            UserContext.set(new LoginUser(adminId, adminName, Statuses.ROLE_ADMIN));
            try {
                startLine.await();
                claimService.review(claimId, true, adminName + " 审核通过");
                return "OK";
            } catch (Exception e) {
                return "FAIL:" + e.getMessage();
            } finally {
                UserContext.clear();
            }
        };
    }

    private Long insertClaim(Long itemId, long applicantId) {
        jdbc.update("INSERT INTO claim(item_id, applicant_id, proof, answer, status, created_at, updated_at) "
                + "VALUES (?, ?, '并发测试证明', '白色划痕', 'PENDING', NOW(), NOW())", itemId, applicantId);
        return jdbc.queryForObject(
                "SELECT id FROM claim WHERE item_id = ? AND applicant_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, itemId, applicantId);
    }

    private int countLogs(Long claimId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM op_log WHERE target_type = 'CLAIM' AND target_id = ?", Integer.class, claimId);
        return n == null ? 0 : n;
    }

    private void cleanTestData() {
        UserContext.clear();
        if (itemId != null) {
            jdbc.update("DELETE FROM op_log WHERE target_type = 'CLAIM' AND target_id IN (SELECT id FROM claim WHERE item_id = ?)", itemId);
            jdbc.update("DELETE FROM op_log WHERE target_type = 'ITEM' AND target_id = ?", itemId);
            jdbc.update("DELETE FROM claim WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
    }

    private long insertUser(String role) {
        String username = "test_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_user(username, password, role, created_at) VALUES (?, 'unused', ?, NOW())", username, role);
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        userIds.add(id);
        return id;
    }
}
