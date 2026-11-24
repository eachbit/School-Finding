package com.project.web;

import com.project.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Result<Map<String, String>>> health() {
        try {
            // 空结果查询验证必需表与字段，避免缺少迁移字段时误报就绪。
            jdbc.queryForList("""
                    SELECT i.id, i.type, i.title, i.description, i.category, i.location,
                           i.happen_time, i.color, i.image_url, i.contact, i.hidden_feature,
                           i.verify_question, i.verify_answer, i.status, i.publisher_id,
                           i.review_opinion, i.created_at, i.updated_at,
                           u.id, u.username, u.password, u.role, u.contact, u.created_at,
                           c.id, c.item_id, c.applicant_id, c.proof, c.answer, c.status,
                           c.review_opinion, c.reviewer_id, c.handover_by, c.created_at, c.updated_at,
                           o.id, o.operator_id, o.operator_name, o.action, o.target_type,
                           o.target_id, o.opinion, o.created_at,
                           p.filename, p.owner_id, p.created_at
                    FROM item i
                    LEFT JOIN sys_user u ON 1=0
                    LEFT JOIN claim c ON 1=0
                    LEFT JOIN op_log o ON 1=0
                    LEFT JOIN uploaded_image p ON 1=0
                    WHERE 1=0
                    """);
            return ResponseEntity.ok(Result.ok(Map.of("application", "School-Finding", "status", "UP", "database", "UP")));
        } catch (org.springframework.dao.DataAccessException failure) {
            return ResponseEntity.status(503).body(Result.fail(503, "数据库或表结构尚未就绪"));
        }
    }
}
