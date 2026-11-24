package com.project.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UploadMapper {
    @Insert("INSERT INTO uploaded_image(filename, owner_id) VALUES(#{filename}, #{ownerId})")
    int insert(@Param("filename") String filename, @Param("ownerId") Long ownerId);

    @Select("SELECT owner_id FROM uploaded_image WHERE filename = #{filename}")
    Long selectOwner(@Param("filename") String filename);
}
