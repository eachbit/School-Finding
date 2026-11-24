package com.project.auth;

import com.project.common.BizException;
import com.project.common.Statuses;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser loginUser = HOLDER.get();
        if (loginUser == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static LoginUser requireAdmin() {
        LoginUser user = require();
        if (!Statuses.isAdmin(user.getRole())) {
            throw BizException.of403("该操作仅管理员可执行");
        }
        return user;
    }
}
