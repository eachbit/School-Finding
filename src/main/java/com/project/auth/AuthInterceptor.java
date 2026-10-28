package com.project.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.Result;
import com.project.common.Statuses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        LoginUser loginUser = tokenStore.get(token);
        if (loginUser == null) {
            write(response, 401, "未登录或登录已过期，请先调用 /api/auth/login 获取 token");
            return false;
        }

        if (request.getRequestURI().startsWith("/api/admin/") && !Statuses.isAdmin(loginUser.getRole())) {
            write(response, 403, "无权限：该接口仅管理员可访问");
            return false;
        }

        UserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (value != null && !value.isBlank()) {
            return value.startsWith(BEARER) ? value.substring(BEARER.length()).trim() : value.trim();
        }

        String simple = request.getHeader("token");
        return simple == null ? null : simple.trim();
    }

    private void write(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
