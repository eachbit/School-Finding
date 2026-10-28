package com.project.mapper;

import com.project.pojo.Claim;
import com.project.vo.ClaimVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClaimMapper {

    int insert(Claim claim);

    Claim selectById(@Param("id") Long id);

    Claim selectByIdForUpdate(@Param("id") Long id);

    ClaimVO selectVOById(@Param("id") Long id);

    Claim selectValidByItemAndApplicant(@Param("itemId") Long itemId, @Param("applicantId") Long applicantId);

    Claim selectApprovedByItem(@Param("itemId") Long itemId);

    List<ClaimVO> selectByApplicant(@Param("applicantId") Long applicantId);

    List<ClaimVO> selectAdminPage(@Param("status") String status,
                                  @Param("itemId") Long itemId,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    long countAdmin(@Param("status") String status, @Param("itemId") Long itemId);

    int updateReview(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reviewOpinion") String reviewOpinion,
                     @Param("reviewerId") Long reviewerId);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("reviewOpinion") String reviewOpinion);

    int updateHandover(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("handoverBy") Long handoverBy);

    long countByStatus(@Param("status") String status);
}
