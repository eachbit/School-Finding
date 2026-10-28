package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
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
class ClaimConcurrencyTest {

    private static final long PUBLISHER_ID = 2L;
    private static final long APPLICANT_A = 1L;
    private static final long APPLICANT_B = 5L;
    private static final long ADMIN_1 = 3L;
    private static final long ADMIN_2 = 4L;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long itemId;
    private Long claimA;
    private Long claimB;

    @BeforeEach
    void setUp() {
        cleanTestData();

        jdbc.update("INSERT INTO sys_user(id, username, password, role, contact, created_at) "
                + "VALUES (4, 'admin2', 'x', 'ADMIN', '13900000001', NOW()) "
                + "ON DUPLICATE KEY UPDATE role = 'ADMIN'");

        jdbc.update("INSERT INTO sys_user(id, username, password, role, contact, created_at) "
                + "VALUES (5, 'student_concurrency', 'x', 'STUDENT', '13800000005', NOW()) "
                + "ON DUPLICATE KEY UPDATE role = 'STUDENT'");

        jdbc.update("INSERT INTO item(type, title, description, category, location, happen_time, color, contact, "
                + "hidden_feature, verify_question, verify_answer, status, publisher_id, created_at, updated_at) "
                + "VALUES ('FOUND', '【并发测试】黑色雨伞', '并发测试数据', '雨具', '图书馆一楼大厅', NOW(), '黑色', '138', "
                + "'伞柄有白色划痕', '伞柄有什么痕迹？', '白色划痕', 'PUBLIC', ?, NOW(), NOW())", PUBLISHER_ID);
        itemId = jdbc.queryForObject("SELECT id FROM item WHERE title = '【并发测试】黑色雨伞'", Long.class);

        claimA = insertClaim(itemId, APPLICANT_A);
        claimB = insertClaim(itemId, APPLICANT_B);
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
            Callable<String> taskA = reviewTask(ADMIN_1, claimA, startLine);
            Callable<String> taskB = reviewTask(ADMIN_2, claimB, startLine);

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
        UserContext.set(new LoginUser(ADMIN_1, "admin", Statuses.ROLE_ADMIN));
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
        String adminName = adminId == ADMIN_1 ? "admin" : "admin2";
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
        jdbc.update("DELETE FROM op_log WHERE target_type = 'CLAIM' AND target_id IN "
                + "(SELECT id FROM claim WHERE item_id IN (SELECT id FROM item WHERE title = '【并发测试】黑色雨伞'))");
        jdbc.update("DELETE FROM claim WHERE item_id IN (SELECT id FROM item WHERE title = '【并发测试】黑色雨伞')");
        jdbc.update("DELETE FROM item WHERE title = '【并发测试】黑色雨伞'");
    }
}
