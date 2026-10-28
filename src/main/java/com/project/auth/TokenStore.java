package com.project.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {

    private final Map<String, LoginUser> users = new ConcurrentHashMap<>();
    private final Map<String, Long> expireAt = new ConcurrentHashMap<>();

    @Value("${app.token.expire-hours:24}")
    private long expireHours;

    public String issue(LoginUser loginUser) {
        String token = UUID.randomUUID().toString().replace("-", "");
        users.put(token, loginUser);
        expireAt.put(token, System.currentTimeMillis() + expireHours * 3600_000L);
        return token;
    }

    public LoginUser get(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Long exp = expireAt.get(token);
        if (exp == null) {
            return null;
        }
        if (exp < System.currentTimeMillis()) {
            remove(token);
            return null;
        }
        return users.get(token);
    }

    public void remove(String token) {
        users.remove(token);
        expireAt.remove(token);
    }
}
