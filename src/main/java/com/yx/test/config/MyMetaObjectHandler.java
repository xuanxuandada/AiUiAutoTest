package com.yx.test.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yx.test.util.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 1. 自动填充 createTime / updateTime
 * 2. 自动盖章「归属用户」userId —— 只要实体里有这个字段，insert 时就会写上「当前登录用户」
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);

        // 自动盖章：只在该字段当前为 null 时才填，所以显式 setUserId 传进来的值不会被覆盖；
        // strictInsertFill 内部会按实体字段名过滤，没有 userId 字段的实体（如 UserAiConfig）完全安全。
        Long userId = TenantContext.getCurrentUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "userId", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
    }
}
