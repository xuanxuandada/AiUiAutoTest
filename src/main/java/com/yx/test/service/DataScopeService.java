package com.yx.test.service;

import java.util.List;

/**
 * 数据可见范围服务 —— 「谁能看到谁的数据」
 *
 * 规则（和用户需求一一对应）：
 *   我 只能看到 自己 创建的 测试用例 / 测试套件 / 测试报告；
 *   除非 某个人 是我的下级（我是他的「上一级」，包括隔级的上级），那他的数据我也能看到。
 *
 * 所以「可见用户集」= {我} ∪ {沿着 leader_id 往下的所有后代}。
 */
public interface DataScopeService {

    /**
     * 取当前登录用户ID；没登录直接抛 401。
     * 用途：所有写操作（新增/改/删）第一步都拿这个，绝不允许用前端传来的 userId。
     */
    Long requireCurrentUserId();

    /**
     * 当前用户「能看到哪些人的数据」，返回值一定包含自己。
     * 同一个租户内计算，遇到 «环»（A的上级是B、B的上级是A）会自动终止，不会死循环。
     */
    List<Long> visibleUserIds();

    /**
     * 判断某条数据（归属人为 ownerUserId）当前用户能不能看。
     * 注意：ownerUserId 为 null（历史老数据没回填）时返回 false —— 谁都不给看，安全第一。
     */
    boolean canAccess(Long ownerUserId);

    /** 和 canAccess 一样，只是不能看时直接抛 403。 */
    void assertCanAccess(Long ownerUserId);
}
