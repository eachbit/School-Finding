package com.project.mapper;

import com.project.pojo.Item;
import com.project.vo.ItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ItemMapper {

    int insert(Item item);

    Item selectById(@Param("id") Long id);

    Item selectByIdForUpdate(@Param("id") Long id);

    ItemVO selectVOById(@Param("id") Long id, @Param("withSensitive") boolean withSensitive);

    int updateContent(Item item);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("reviewOpinion") String reviewOpinion);

    List<ItemVO> selectPublicPage(@Param("type") String type,
                                  @Param("category") String category,
                                  @Param("location") String location,
                                  @Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    long countPublic(@Param("type") String type,
                     @Param("category") String category,
                     @Param("location") String location,
                     @Param("keyword") String keyword);

    List<ItemVO> selectByPublisher(@Param("publisherId") Long publisherId,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    long countByPublisher(@Param("publisherId") Long publisherId, @Param("status") String status);

    List<ItemVO> selectAdminPage(@Param("status") String status,
                                 @Param("type") String type,
                                 @Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countAdmin(@Param("status") String status,
                    @Param("type") String type,
                    @Param("keyword") String keyword);

    List<Item> selectMatchCandidates(@Param("type") String type, @Param("limit") int limit);

    long countByStatusAndType(@Param("status") String status, @Param("type") String type);
}
