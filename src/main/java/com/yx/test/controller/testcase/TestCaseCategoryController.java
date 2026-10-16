package com.yx.test.controller.testcase;

import com.yx.test.common.BizException;
import com.yx.test.common.Result;
import com.yx.test.entity.TestCaseCategory;
import com.yx.test.service.TestCaseCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试用例分类管理接口 (RESTful)
 */
@RestController
@RequestMapping("/api/case/categories")
@RequiredArgsConstructor
public class TestCaseCategoryController {

    private final TestCaseCategoryService categoryService;

    /**
     * 查询所有分类（树形结构） GET /api/case/categories/tree
     */
    @GetMapping("/tree")
    public Result<List<TestCaseCategory>> getTreeList(@RequestParam Long tenantId) {
        List<TestCaseCategory> treeList = categoryService.getTreeList(tenantId);
        return Result.success(treeList);
    }

    /**
     * 查询所有分类（平铺列表） GET /api/case/categories
     */
    @GetMapping
    public Result<List<TestCaseCategory>> list(@RequestParam(required = false) Long tenantId,
                                               @RequestParam(required = false) Long parentId) {
        List<TestCaseCategory> categories;
        if (tenantId != null) {
            categories = categoryService.listByTenantId(tenantId);
        } else if (parentId != null) {
            categories = categoryService.listByParentId(parentId);
        } else {
            categories = categoryService.list();
        }
        return Result.success(categories);
    }

    /**
     * 根据ID查询分类 GET /api/case/categories/{id}
     */
    @GetMapping("/{id}")
    public Result<TestCaseCategory> getById(@PathVariable Long id) {
        TestCaseCategory category = categoryService.getById(id);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        return Result.success(category);
    }

    /**
     * 创建分类 POST /api/case/categories
     */
    @PostMapping
    public Result<TestCaseCategory> create(@RequestBody TestCaseCategory category) {
        // 校验必填字段
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw BizException.badRequest("分类名称不能为空");
        }
        if (category.getCode() == null || category.getCode().trim().isEmpty()) {
            throw BizException.badRequest("分类编码不能为空");
        }
        if (category.getTenantId() == null) {
            throw BizException.badRequest("租户ID不能为空");
        }

        // 设置默认值
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (category.getStatus() == null) {
            category.setStatus("ACTIVE");
        }

        categoryService.save(category);
        return Result.success("分类创建成功", category);
    }

    /**
     * 更新分类 PUT /api/case/categories/{id}
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TestCaseCategory category) {
        // 校验
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw BizException.badRequest("分类名称不能为空");
        }

        category.setId(id);
        boolean success = categoryService.updateById(category);
        if (!success) {
            throw BizException.internalError("更新失败");
        }
        return Result.success();
    }

    /**
     * 删除分类 DELETE /api/case/categories/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 检查是否有子分类
        List<TestCaseCategory> children = categoryService.listByParentId(id);
        if (!children.isEmpty()) {
            throw BizException.badRequest("该分类下还有子分类，不能删除");
        }

        boolean success = categoryService.removeById(id);
        if (!success) {
            throw BizException.internalError("删除失败");
        }
        return Result.success();
    }
}
