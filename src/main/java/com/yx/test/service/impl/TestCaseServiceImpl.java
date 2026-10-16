package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.TestCase;
import com.yx.test.mapper.TestCaseMapper;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl extends ServiceImpl<TestCaseMapper, TestCase> implements TestCaseService {

    /** 数据可见范围服务：算「我 + 我的所有下级」 */
    private final DataScopeService dataScopeService;

    @Override
    public Page<TestCase> listVisible(String type, String tags, String keyword, long page, long pageSize) {
        // 关键一行：in(getUserId, 可见用户集)。
        // 这句话把「别人的用例」直接挡在 SQL 外面（不是查出来再在 Java 里过滤），
        // 所以既安全、又不会白搬一堆不该看的数据。
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .in(TestCase::getUserId, dataScopeService.visibleUserIds());

        if (tags != null) {
            wrapper.like(TestCase::getTags, tags).eq(TestCase::getStatus, "ACTIVE");
        } else if (type != null) {
            wrapper.eq(TestCase::getType, type).eq(TestCase::getStatus, "ACTIVE");
        }

        // 搜索框下推：按用例名称模糊匹配，避免前端拿全量再本地 filter
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(TestCase::getName, keyword.trim());
        }

        // 分页必须配一个确定的排序：否则 MySQL 每次返回的顺序可能不同，
        // 翻到第 2 页时会出现「和第一页重复」或者「有条用例压根没看得到」。
        wrapper.orderByAsc(TestCase::getId);
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public List<TestCase> listByTags(String tags) {
        return list(new LambdaQueryWrapper<TestCase>()
                .in(TestCase::getUserId, dataScopeService.visibleUserIds())
                .like(TestCase::getTags, tags)
                .eq(TestCase::getStatus, "ACTIVE"));
    }

    @Override
    public List<TestCase> listByType(String type) {
        return list(new LambdaQueryWrapper<TestCase>()
                .in(TestCase::getUserId, dataScopeService.visibleUserIds())
                .eq(TestCase::getType, type)
                .eq(TestCase::getStatus, "ACTIVE"));
    }

    @Override
    public boolean saveBatch(List<TestCase> caseList) {
        return saveBatch(caseList, 100);
    }
}
