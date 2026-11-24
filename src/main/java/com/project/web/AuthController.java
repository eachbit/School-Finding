package com.project.web;

import com.project.common.Result;
import com.project.auth.AuthInterceptor;
import com.project.dto.LoginRequest;
import com.project.dto.RegisterRequest;
import com.project.dto.ProfileRequest;
import com.project.pojo.User;
import com.project.service.AuthService;
import com.project.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-认证", description = "注册、登录、当前用户")
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册（注册出来都是学生）")
    @PostMapping("/auth/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @Operation(summary = "登录，返回 token")
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(authService.currentUser());
    }

    @Operation(summary = "更新自己的联系方式")
    @PutMapping("/me")
    public Result<User> updateProfile(@Valid @RequestBody ProfileRequest req) {
        return Result.ok(authService.updateProfile(req));
    }

    @Operation(summary = "退出登录并撤销当前令牌")
    @PostMapping("/auth/logout")
    public Result<Void> logout(@RequestAttribute(AuthInterceptor.TOKEN_ATTRIBUTE) String token) {
        authService.logout(token);
        return Result.ok();
    }
}
