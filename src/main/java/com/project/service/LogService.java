package com.project.service;

import com.project.auth.LoginUser;
import com.project.mapper.OpLogMapper;
import com.project.pojo.OpLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    private final OpLogMapper opLogMapper;

    public LogService(OpLogMapper opLogMapper) {
        this.opLogMapper = opLogMapper;
    }

    public void record(LoginUser operator, String action, String targetType, Long targetId, String opinion) {
        OpLog log = new OpLog();
        log.setOperatorId(operator.getId());
        log.setOperatorName(operator.getUsername());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOpinion(opinion);
        opLogMapper.insert(log);
    }

    public List<OpLog> recent(int limit) {
        return opLogMapper.selectRecent(limit);
    }
}
