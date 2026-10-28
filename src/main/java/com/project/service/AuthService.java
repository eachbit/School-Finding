package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.TokenStore;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import com.project.dto.LoginRequest;
import com.project.dto.RegisterRequest;
import com.project.mapper.UserMapper;
import com.project.pojo.User;
import com.project.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, TokenStore tokenStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequest req) {
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw BizException.of400("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());

        user.setPassword(passwordEncoder.encode(req.getPassword()));

        user.setRole(Statuses.ROLE_STUDENT);
        user.setContact(req.getContact());
        userMapper.insert(user);
        return issueToken(user);
    }

    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername());

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return issueToken(user);
    }

    public User currentUser() {
        LoginUser me = UserContext.require();
        User user = userMapper.selectById(me.getId());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return user;
    }

    private LoginVO issueToken(User user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(tokenStore.issue(loginUser));
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setContact(user.getContact());
        return vo;
    }
}
