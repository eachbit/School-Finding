package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.LoginUser;
import com.project.auth.TokenStore;
import com.project.common.Statuses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiWorkflowTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenStore tokens;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<String> issuedTokens = new ArrayList<>();
    private String owner;
    private String applicant;
    private String admin;
    private Long applicantId;
    private final String itemTitle = "API回归黑色雨伞" + UUID.randomUUID();

    @BeforeEach
    void createUsers() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).endsWith("_test"));
        owner = createUser(Statuses.ROLE_STUDENT);
        applicant = createUser(Statuses.ROLE_STUDENT);
        applicantId = userIds.get(1);
        admin = createUser(Statuses.ROLE_ADMIN);
    }

    @AfterEach
    void cleanup() {
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM op_log WHERE target_type = 'CLAIM' AND target_id IN (SELECT id FROM claim WHERE item_id = ?)", itemId);
            jdbc.update("DELETE FROM op_log WHERE target_type = 'ITEM' AND target_id = ?", itemId);
            jdbc.update("DELETE FROM claim WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
        issuedTokens.forEach(tokens::remove);
    }

    @Test
    void publishingReviewClaimRevocationAndHandoverUseRealRows() throws Exception {
        long itemId = createItem();
        assertEquals("PENDING", request(get("/api/items/{id}", itemId), owner, null).path("data").path("status").asText());
        assertEquals(404, request(get("/api/items/{id}", itemId), applicant, null).path("code").asInt());
        assertEquals(0, request(get("/api/items").param("keyword", itemTitle), applicant, null).path("data").path("total").asInt());
        assertEquals(403, request(post("/api/admin/items/{id}/review", itemId), applicant, Map.of("pass", true)).path("code").asInt());
        assertEquals(400, request(post("/api/admin/items/{id}/review", itemId), admin, Map.of("pass", false)).path("code").asInt());
        assertEquals(0, request(post("/api/admin/items/{id}/review", itemId), admin, Map.of("pass", true)).path("code").asInt());

        JsonNode publicItem = request(get("/api/items/{id}", itemId), applicant, null).path("data");
        assertFalse(publicItem.has("contact"));
        assertFalse(publicItem.has("hiddenFeature"));
        assertFalse(publicItem.has("verifyAnswer"));
        assertEquals("伞柄有什么痕迹？", publicItem.path("verifyQuestion").asText());
        JsonNode publicList = request(get("/api/items").param("keyword", itemTitle), applicant, null).path("data");
        assertEquals(1, publicList.path("total").asInt());
        JsonNode listItem = publicList.path("records").get(0);
        assertEquals(itemId, listItem.path("id").asLong());
        assertFalse(listItem.has("contact"));
        assertFalse(listItem.has("hiddenFeature"));
        assertFalse(listItem.has("verifyAnswer"));
        assertEquals("白色划痕", request(get("/api/items/{id}", itemId), admin, null).path("data").path("verifyAnswer").asText());
        assertEquals(400, request(post("/api/items/{id}/claims", itemId), owner, Map.of("proof", "自己的物品", "answer", "白色划痕")).path("code").asInt());
        assertEquals(400, request(post("/api/items/{id}/claims", itemId), applicant, Map.of("proof", "拥有证明")).path("code").asInt());
        JsonNode submitted = request(post("/api/items/{id}/claims", itemId), applicant,
                Map.of("proof", "拥有该物品的具体证明", "answer", "白色划痕"));
        assertEquals(0, submitted.path("code").asInt());
        assertFalse(submitted.path("data").has("itemHiddenFeature"));
        assertFalse(submitted.path("data").has("itemVerifyAnswer"));
        long claimId = submitted.path("data").path("id").asLong();
        assertEquals(403, request(post("/api/claims/{id}/cancel", claimId), owner, null).path("code").asInt());
        assertEquals(0, request(get("/api/me/claims"), owner, null).path("data").size());
        assertEquals(1, request(get("/api/me/claims"), applicant, null).path("data").size());
        assertEquals(0, request(post("/api/admin/claims/{id}/review", claimId), admin, Map.of("pass", true)).path("code").asInt());
        assertEquals("PUBLIC", jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, itemId));
        assertEquals(400, request(post("/api/items/{id}/close", itemId), owner, null).path("code").asInt());
        assertEquals(0, request(post("/api/admin/claims/{id}/revoke", claimId).param("opinion", "重新核验证明"), admin, null).path("code").asInt());
        assertEquals(400, request(post("/api/admin/claims/{id}/handover", claimId), admin, null).path("code").asInt());
        long replacement = request(post("/api/items/{id}/claims", itemId), applicant,
                Map.of("proof", "补充证明", "answer", "白色划痕")).path("data").path("id").asLong();
        assertTrue(replacement > claimId);
        assertEquals(0, request(post("/api/admin/claims/{id}/review", replacement), admin, Map.of("pass", true)).path("code").asInt());
        assertEquals(0, request(post("/api/admin/claims/{id}/handover", replacement), admin, null).path("code").asInt());
        assertEquals("CLAIMED", jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, itemId));
        assertEquals("HANDED", jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, replacement));
        assertEquals(400, request(post("/api/admin/claims/{id}/handover", replacement), admin, null).path("code").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM op_log WHERE target_id = ? AND action = 'CLAIM_HANDOVER'", Integer.class, replacement));
    }

    @Test
    void contactChangesPersistAndLogoutRevokesToken() throws Exception {
        assertEquals(0, request(put("/api/me"), applicant, Map.of("contact", " 13800000000 ", "role", "ADMIN", "id", userIds.get(0))).path("code").asInt());
        assertEquals("13800000000", jdbc.queryForObject("SELECT contact FROM sys_user WHERE id = ?", String.class, applicantId));
        JsonNode me = request(get("/api/me"), applicant, null).path("data");
        assertEquals("STUDENT", me.path("role").asText());
        assertEquals(applicantId.longValue(), me.path("id").asLong());
        assertFalse(me.has("password"));
        assertEquals(0, request(post("/api/auth/logout"), applicant, null).path("code").asInt());
        assertEquals(401, request(get("/api/me"), applicant, null).path("code").asInt());
    }

    @Test
    void malformedJsonAndOversizedFieldsReturnClientError() throws Exception {
        String malformed = "{\"happenTime\":\"bad-time\"}";
        JsonNode response = json.readTree(mvc.perform(post("/api/items").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(malformed)).andReturn().getResponse().getContentAsByteArray());
        assertEquals(400, response.path("code").asInt());
        assertEquals(400, request(put("/api/me"), applicant, Map.of("contact", "x".repeat(101))).path("code").asInt());
        assertEquals(401, request(get("/api/me"), null, null).path("code").asInt());
    }

    @Test
    void registrationStoresHashedPasswordAndRejectsDuplicateUsername() throws Exception {
        String username = "reg_" + UUID.randomUUID().toString().substring(0, 12);
        Map<String, String> body = Map.of("username", username, "password", "test-password", "contact", "138");
        JsonNode created = request(post("/api/auth/register"), null, body);
        assertEquals(0, created.path("code").asInt());
        userIds.add(created.path("data").path("userId").asLong());
        issuedTokens.add(created.path("data").path("token").asText());
        String stored = jdbc.queryForObject("SELECT password FROM sys_user WHERE username = ?", String.class, username);
        assertTrue(stored.startsWith("$2"));
        assertEquals("STUDENT", created.path("data").path("role").asText());
        assertEquals(400, request(post("/api/auth/register"), null, body).path("code").asInt());
        assertEquals(401, request(post("/api/auth/login"), null, Map.of("username", username, "password", "wrong-password")).path("code").asInt());
        JsonNode login = request(post("/api/auth/login"), null, body);
        assertEquals(0, login.path("code").asInt());
        issuedTokens.add(login.path("data").path("token").asText());
    }

    private long createItem() throws Exception {
        JsonNode created = request(post("/api/items"), owner, Map.of(
                "type", "FOUND", "title", itemTitle, "description", "图书馆捡到的雨伞",
                "category", "雨具", "location", "图书馆", "happenTime", "2026-01-01 10:00:00",
                "contact", "13800000001", "hiddenFeature", "伞柄白色划痕", "verifyQuestion", "伞柄有什么痕迹？", "verifyAnswer", "白色划痕"));
        assertEquals(0, created.path("code").asInt(), created.toString());
        long id = created.path("data").path("id").asLong();
        itemIds.add(id);
        return id;
    }

    private String createUser(String role) {
        String username = "api_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_user(username,password,role,created_at) VALUES (?, 'unused', ?, NOW())", username, role);
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        userIds.add(id);
        String token = tokens.issue(new LoginUser(id, username, role));
        issuedTokens.add(token);
        return token;
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        if (token != null) {
            builder.header("Authorization", token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return json.readTree(mvc.perform(builder).andReturn().getResponse().getContentAsByteArray());
    }
}
