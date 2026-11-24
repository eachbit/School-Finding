package com.project.mapper;

import com.project.pojo.OpLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpLogMapper {

    int insert(OpLog log);

    List<OpLog> selectRecent(@Param("limit") int limit);
}
