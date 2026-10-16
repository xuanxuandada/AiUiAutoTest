package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yx.test.common.BizException;
import com.yx.test.entity.User;
import com.yx.test.mapper.UserMapper;
import com.yx.test.service.DataScopeService;
import com.yx.test.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据可见范围实现 —— 用 BFS（广度优先）把「我 + 我的所有下级」串出来。
 *
 * 为什么是 BFS 而不是一句 SQL？
 *   因为上下级是 «链式» 的（我是张三的上级，张三又是李四的上级），
 *   MySQL 里要么写递归 CTE、要么一条条查（N+1）。这里改成：
 *   一次查出全租户的 (id, leader_id) → 在内存里拼出「谁的下级是谁」→ 从我开始往下走一遍。
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private final UserMapper userMapper;

    @Override
    public Long requireCurrentUserId() {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录，请先登录");
        }
        return userId;
    }

    @Override
    public List<Long> visibleUserIds() {
        Long me = requireCurrentUserId();
        Long tenantId = TenantContext.getCurrentTenantId();

        // 1. 只查需要的两列，限定同一个租户（不同公司的人不能互相看见）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getLeaderId);
        if (tenantId != null) {
            wrapper.eq(User::getTenantId, tenantId);
        }
        List<User> all = userMapper.selectList(wrapper);

        // 2. 把「上级 -> 他的下级列表」做成一张表，方便从我开始往下找
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (User u : all) {
            if (u.getLeaderId() == null) {
                continue; // 没写上级的人不构成任何下级关系
            }
            childrenOf.computeIfAbsent(u.getLeaderId(), k -> new ArrayList<>()).add(u.getId());
        }

        // 3. 从「我」出发，逐层收集下级
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>(); // visited 既是去重，也是防环（A↔B 互相设上级不会死循环）
        Deque<Long> queue = new ArrayDeque<>();

        queue.add(me);
        visited.add(me);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);

            List<Long> children = childrenOf.get(current);
            if (children == null) {
                continue;
            }
            for (Long child : children) {
                if (child == null || visited.contains(child)) {
                    continue; // 已经走过的节点不再入队 —— 保证最多每人走一次，必然结束
                }
                visited.add(child);
                queue.add(child);
            }
        }
        return result;
    }

    @Override
    public boolean canAccess(Long ownerUserId) {
        if (ownerUserId == null) {
            // 历史老数据没回填归属人 —— 严格一点：谁都不给看（而不是谁都能看）
            return false;
        }
        if (TenantContext.getCurrentUserId() == null) {
            return false; // 没登录（例如 Runner 回调线程）不给看
        }
        return visibleUserIds().contains(ownerUserId);
    }

    @Override
    public void assertCanAccess(Long ownerUserId) {
        if (!canAccess(ownerUserId)) {
            throw BizException.forbidden("无权访问该数据（它不属于你，也不属于你的下级）");
        }
    }
}
