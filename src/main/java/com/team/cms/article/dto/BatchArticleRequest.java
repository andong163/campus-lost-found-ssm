package com.team.cms.article.dto;

import com.team.cms.common.api.BatchIdsRequest;
import com.team.cms.common.exception.BusinessException;
import java.util.Map;
import java.util.HashSet;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

/** 保留公共 ids 契约，versions 的键为 ID 的十进制字符串。 */
public class BatchArticleRequest extends BatchIdsRequest {
    @NotNull(message = "批量版本不能为空")
    private Map<Long, @NotNull(message = "版本不能为空") @Min(value = 0, message = "版本不能为负数") Integer> versions;
    public Map<Long,Integer> getVersions() { return versions; }
    public void setVersions(Map<Long,Integer> value) { versions=value; }

    /** Controller 的 @Valid 校验基础字段后，只核对 ID 与版本映射的对应关系。 */
    public void validateVersions() {
        if (!versions.keySet().equals(new HashSet<>(getIds()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "批量 ID 与版本必须一一对应");
        }
    }
}
