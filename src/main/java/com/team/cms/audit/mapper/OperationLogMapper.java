package com.team.cms.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.audit.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/** sys_operation_log 数据访问；查询过滤条件在 AuditService 内用参数化 Wrapper 组合。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
