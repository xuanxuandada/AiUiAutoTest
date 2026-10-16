package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.TestCaseCategory;
import com.yx.test.mapper.TestCaseCategoryMapper;
import com.yx.test.service.TestCaseCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TestCaseCategoryServiceImpl extends ServiceImpl<TestCaseCategoryMapper, TestCaseCategory> implements TestCaseCategoryService {

    @Override
    public List<TestCaseCategory> listByTenantId(Long tenantId) {
        LambdaQueryWrapper<TestCaseCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCaseCategory::getTenantId, tenantId)
                .eq(TestCaseCategory::getStatus, "ACTIVE")
                .orderByAsc(TestCaseCategory::getSortOrder);
        return this.list(wrapper);
    }

    @Override
    public List<TestCaseCategory> listByParentId(Long parentId) {
        LambdaQueryWrapper<TestCaseCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCaseCategory::getParentId, parentId)
                .eq(TestCaseCategory::getStatus, "ACTIVE")
                .orderByAsc(TestCaseCategory::getSortOrder);
        return this.list(wrapper);
    }

    @Override
    public List<TestCaseCategory> getTreeList(Long tenantId) {
        // 1. 查询该租户下所有分类
        List<TestCaseCategory> allCategories = listByTenantId(tenantId);
        
        // 2. 按 parentId 分组
        Map<Long, List<TestCaseCategory>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(TestCaseCategory::getParentId));
        
        // 3. 构建树形结构（递归设置 children）
        return buildTree(allCategories, parentMap, 0L);
    }

    /**
     * 递归构建树形结构
     */
    private List<TestCaseCategory> buildTree(List<TestCaseCategory> allCategories, 
                                             Map<Long, List<TestCaseCategory>> parentMap, 
                                             Long parentId) {
        List<TestCaseCategory> children = parentMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        
        for (TestCaseCategory category : children) {
            // 递归查找子分类并设置到 children 字段
            List<TestCaseCategory> subChildren = buildTree(allCategories, parentMap, category.getId());
            category.setChildren(subChildren);
        }
        
        return children;
    }
}
