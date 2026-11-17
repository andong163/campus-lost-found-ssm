package com.team.cms.article.dto;

import com.team.cms.common.api.BatchIdsRequest;
import com.team.cms.common.exception.BusinessException;
import java.util.Map;
import java.util.HashSet;
import org.springframework.http.HttpStatus;

/** 保留公共 ids 契约，versions 的键为 ID 的十进制字符串。 */
public class BatchArticleRequest extends BatchIdsRequest {
    private Map<Long,Integer> versions;
    public Map<Long,Integer> getVersions() { return versions; }
    public void setVersions(Map<Long,Integer> value) { versions=value; }
    public void validate() {
        if (getIds()==null || getIds().isEmpty() || getIds().size()>100 ||
            getIds().stream().anyMatch(id->id==null||id<=0) ||
            new HashSet<>(getIds()).size()!=getIds().size() || versions==null ||
            !versions.keySet().equals(new HashSet<>(getIds())) ||
            versions.values().stream().anyMatch(v->v==null||v<0))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"批量 ID 或版本不合法");
    }
}
