package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.TokenStore;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.common.Statuses;
import com.project.dto.LoginRequest;
import com.project.dto.RegisterRequest;
import com.project.dto.ProfileRequest;
import com.project.mapper.UserMapper;
import com.project.pojo.User;
import com.project.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

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
        req.setUsername(req.getUsername().trim());
        if (req.getUsername().length() < 4 || req.getUsername().length() > 20) {
            throw BizException.of400("用户名去除首尾空格后长度需为 4~20");
        }
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw BizException.of400("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());

        user.setPassword(passwordEncoder.encode(req.getPassword()));

        user.setRole(Statuses.ROLE_STUDENT);
        user.setContact(req.getContact());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.of400("用户名已存在");
        }
        return issueToken(user);
    }

    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername().trim());

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

    public void logout(String token) {
        tokenStore.remove(token);
    }

    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(ProfileRequest req) {
        LoginUser me = UserContext.require();
        currentUser();
        userMapper.updateContact(me.getId(), req.getContact().trim());
        return currentUser();
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
