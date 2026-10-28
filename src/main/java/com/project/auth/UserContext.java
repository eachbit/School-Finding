package com.project.auth;

import com.project.common.BizException;

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
}
