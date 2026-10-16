package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.TestCaseCategory;

import java.util.List;

public interface TestCaseCategoryService extends IService<TestCaseCategory> {

    /**
     * 根据租户ID查询分类列表
     */
    List<TestCaseCategory> listByTenantId(Long tenantId);

    /**
     * 根据父分类ID查询子分类
     */
    List<TestCaseCategory> listByParentId(Long parentId);

    /**
     * 查询树形结构的分类列表（包含子分类）
     */
    List<TestCaseCategory> getTreeList(Long tenantId);
}
