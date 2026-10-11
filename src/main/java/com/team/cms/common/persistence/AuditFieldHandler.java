package com.team.cms.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

public class AuditFieldHandler implements MetaObjectHandler {
    @Override public void insertFill(MetaObject object) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(object, "createdAt", LocalDateTime.class, now);
        strictInsertFill(object, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(object, "deleted", Integer.class, 0);
        strictInsertFill(object, "version", Integer.class, 0);
    }
    @Override public void updateFill(MetaObject object) {
        // Replace stale updatedAt loaded from a preceding SELECT.
        setFieldValByName("updatedAt", LocalDateTime.now(), object);
    }
}
