package com.yx.test.util;
/**
 * 租户上下文 —— 存储当前请求的用户信息
 * 使用ThreadLocal，每个请求线程有独立的数据副本，互不干扰
 */
public class TenantContext {
    /**
    * ThreadLocal，每个请求线程有独立的数据副本，互不干扰，这新建的是一个线程的变量
    * */
    private  static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private  static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private  static final ThreadLocal<Long> ROLE_ID = new ThreadLocal<>();

    /** 存入用户信息 */
    public static void setCurrentUserId(Long userId,Long tenantId,Long roleId) {
        USER_ID.set(userId);
        TENANT_ID.set(tenantId);
        ROLE_ID.set(roleId);
    }

    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    public static Long getCurrentTenantId() {
        return TENANT_ID.get();
    }

    public static Long getCurrentRoleId() {
        return ROLE_ID.get();
    }

    /** 清理（请求结束后必须调用，防止内存泄漏） */
    public static void remove(){
        USER_ID.remove();
        TENANT_ID.remove();
        ROLE_ID.remove();
    }


}
