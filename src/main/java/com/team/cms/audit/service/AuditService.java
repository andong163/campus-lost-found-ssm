package com.team.cms.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.audit.dto.OperationLogQuery;
import com.team.cms.audit.dto.OperationLogView;
import com.team.cms.audit.entity.OperationLog;
import com.team.cms.audit.mapper.OperationLogMapper;
import com.team.cms.common.api.PageResult;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.team.cms.common.exception.BusinessException;

/**
 * 审计写入与授权查询。
 *
 * <p>写入使用 REQUIRES_NEW：业务事务提交/回滚与审计记录互不牵连；写入异常由 AuditTemplate 捕获，
 * 因此不会把已经成功的业务变成 500，也不会诱导前端重复提交。这里不做敏感字段脱敏，
 * 因为调用方根本不应把正文、联系方式、证据或密码传入 AuditEntry。</p>
 */
@Service
public class AuditService implements AuditWriter {

    private static final int MAX_OPERATOR_NAME = 80;
    private static final int MAX_MODULE = 50;
    private static final int MAX_ACTION = 80;
    private static final int MAX_REQUEST_ID = 36;

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(AuditEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getModule())
                || !StringUtils.hasText(entry.getAction())) {
            throw new IllegalArgumentException("审计模块和动作不能为空");
        }
        OperationLog log = new OperationLog();
        log.setOperatorId(entry.getOperatorId());
        log.setOperatorName(trim(entry.getOperatorName(), MAX_OPERATOR_NAME, ""));
        log.setModule(trim(entry.getModule(), MAX_MODULE, "unknown"));
        log.setAction(trim(entry.getAction(), MAX_ACTION, "unknown"));
        log.setTargetId(entry.getTargetId());
        log.setRequestId(trim(entry.getRequestId(), MAX_REQUEST_ID, "-"));
        log.setResult("FAILURE".equals(entry.getResult()) ? "FAILURE" : "SUCCESS");
        log.setElapsedMs(Math.max(0L, entry.getElapsedMs()));
        log.setCreatedAt(entry.getCreatedAt() == null ? LocalDateTime.now() : entry.getCreatedAt());
        operationLogMapper.insert(log);
    }

    /** 管理员日志查询：操作者、动作、结果、时间范围；排序固定为时间倒序 + ID 倒序。 */
    public PageResult<OperationLogView> query(OperationLogQuery query) {
        if (query.getFrom() != null && query.getTo() != null && query.getFrom().isAfter(query.getTo())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
        }
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getOperatorId() != null, OperationLog::getOperatorId, query.getOperatorId())
            .like(StringUtils.hasText(query.getOperatorName()), OperationLog::getOperatorName, query.getOperatorName())
            .like(StringUtils.hasText(query.getAction()), OperationLog::getAction, query.getAction())
            .eq(StringUtils.hasText(query.getResult()), OperationLog::getResult, query.getResult())
            .ge(query.getFrom() != null, OperationLog::getCreatedAt, query.getFrom())
            .le(query.getTo() != null, OperationLog::getCreatedAt, query.getTo())
            .orderByDesc(OperationLog::getCreatedAt)
            .orderByDesc(OperationLog::getId);
        Page<OperationLog> page = operationLogMapper.selectPage(
            new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.from(page.convert(OperationLogView::from));
    }

    private static String trim(String value, int maxLength, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
