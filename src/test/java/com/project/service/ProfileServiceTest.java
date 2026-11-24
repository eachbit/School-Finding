package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.TokenStore;
import com.project.auth.UserContext;
import com.project.common.Statuses;
import com.project.dto.ProfileRequest;
import com.project.dto.RegisterRequest;
import com.project.common.BizException;
import com.project.mapper.UserMapper;
import com.project.pojo.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {
    @Test
    void registrationValidatesUsernameLengthAfterTrimming() {
        AuthService service = new AuthService(mock(UserMapper.class), mock(PasswordEncoder.class), mock(TokenStore.class));
        RegisterRequest req = new RegisterRequest();
        req.setUsername(" a  ");
        req.setPassword("123456");
        assertThrows(BizException.class, () -> service.register(req));
    }

    @Test
    void logoutRevokesCurrentToken() {
        TokenStore tokens = mock(TokenStore.class);
        AuthService service = new AuthService(mock(UserMapper.class), mock(PasswordEncoder.class), tokens);
        service.logout("active-token");
        verify(tokens).remove("active-token");
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void profileUpdatesCurrentUsersContactOnly() {
        UserMapper users = mock(UserMapper.class);
        AuthService service = new AuthService(users, mock(PasswordEncoder.class), mock(TokenStore.class));
        UserContext.set(new LoginUser(9L, "student", Statuses.ROLE_STUDENT));
        User updated = new User();
        updated.setId(9L);
        updated.setContact("13800000000");
        when(users.selectById(9L)).thenReturn(updated);
        ProfileRequest request = new ProfileRequest();
        request.setContact(" 13800000000 ");
        assertEquals("13800000000", service.updateProfile(request).getContact());
        verify(users).updateContact(9L, "13800000000");
    }
}
