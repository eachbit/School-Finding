package com.project.config;

import com.project.mapper.UserMapper;
import com.project.pojo.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class AdminBootstrap implements ApplicationRunner {
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserMapper users, PasswordEncoder encoder,
                          @Value("${ADMIN_USERNAME:admin}") String username,
                          @Value("${ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (password.isBlank()) {
            return;
        }
        User existing = users.selectByUsername(username);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                throw new IllegalStateException("初始化管理员用户名已被学生账号使用，请设置 ADMIN_USERNAME");
            }
            return;
        }
        if (username.isBlank() || username.length() > 50 || password.length() < 10 || password.length() > 64) {
            throw new IllegalStateException("管理员用户名须为 1–50 字，密码须为 10–64 字");
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(encoder.encode(password));
        admin.setRole("ADMIN");
        users.insert(admin);
    }
}
