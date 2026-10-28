package com.project.mapper;

import com.project.pojo.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    int insert(User user);
}
