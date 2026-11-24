package com.project.service;

import com.project.web.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test
    void reportsIncompleteSchemaAsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenThrow(
                new BadSqlGrammarException("health", "SELECT", new SQLException("missing image_url")));
        var response = new HealthController(jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().getCode());
    }

    @Test
    void checksMigrationFieldsAndAllBusinessTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        var response = new HealthController(jdbc).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().getData().get("database"));
        verify(jdbc).queryForList(argThat(sql -> sql.contains("image_url") && sql.contains("uploaded_image")
                && sql.contains("claim") && sql.contains("op_log") && sql.contains("sys_user")));
    }
}
