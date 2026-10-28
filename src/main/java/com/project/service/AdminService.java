package com.project.service;

import com.project.common.Statuses;
import com.project.mapper.ClaimMapper;
import com.project.mapper.ItemMapper;
import com.project.pojo.OpLog;
import com.project.vo.StatisticsVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final ItemMapper itemMapper;
    private final ClaimMapper claimMapper;
    private final LogService logService;

    public AdminService(ItemMapper itemMapper, ClaimMapper claimMapper, LogService logService) {
        this.itemMapper = itemMapper;
        this.claimMapper = claimMapper;
        this.logService = logService;
    }

    public StatisticsVO statistics() {
        StatisticsVO vo = new StatisticsVO();
        vo.setPublicFoundCount(itemMapper.countByStatusAndType(Statuses.ITEM_PUBLIC, Statuses.TYPE_FOUND));
        vo.setClaimedFoundCount(itemMapper.countByStatusAndType(Statuses.ITEM_CLAIMED, Statuses.TYPE_FOUND));
        vo.setPendingClaimCount(claimMapper.countByStatus(Statuses.CLAIM_PENDING));
        return vo;
    }

    public List<OpLog> recentLogs(int limit) {
        return logService.recent(Math.min(Math.max(limit, 1), 200));
    }
}
