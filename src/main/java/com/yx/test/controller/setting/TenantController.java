package com.yx.test.controller.setting;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.test.common.Result;
import com.yx.test.entity.Tenant;
import com.yx.test.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户管理接口 (RESTful)
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /** 分页查询租户 GET /api/tenants?page=1&pageSize=20 */
    @GetMapping
    public Result<Page<Tenant>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<Tenant> pageParam = new Page<>(page, pageSize);
        return Result.success(tenantService.page(pageParam));
    }

    /** 获取租户ID和名称列表（下拉选择用） GET /api/tenants/options?page=1&pageSize=20 */
    @GetMapping("/options")
    public Result<Page<Map<String, Object>>> options(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // 分页查询启用状态的租户
        Page<Tenant> pageParam = new Page<>(page, pageSize);
        Page<Tenant> tenantPage = tenantService.lambdaQuery()
                .eq(Tenant::getStatus, "ACTIVE")
                .page(pageParam);
        
        // 转换为只返回 id 和 name
        Page<Map<String, Object>> result = new Page<>(tenantPage.getCurrent(), tenantPage.getSize(), tenantPage.getTotal());
        result.setRecords(tenantPage.getRecords().stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "name", t.getName()
                ))
                .collect(Collectors.toList()));
        return Result.success(result);
    }

    /**
     * 租户搜索 GET /api/tenants/search?keyword=xxx&page=1&pageSize=20
     * 前端传一个keyword，后端自动搜索名称、编码、联系人（OR关系，任一匹配即返回）
     */
    @GetMapping("/search")
    public Result<Page<Tenant>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        Page<Tenant> pageParam = new Page<>(page, pageSize);
        
        // 没传参数或为空，返回分页默认数据
        if (keyword == null || keyword.isBlank()) {
            return Result.success(tenantService.page(pageParam));
        }
        
        // 使用OR条件搜索，名称/编码/联系人任一匹配即返回
        Page<Tenant> result = tenantService.lambdaQuery()
                .like(Tenant::getName, keyword)
                .or()
                .like(Tenant::getCode, keyword)
                .or()
                .like(Tenant::getContact, keyword)
                .page(pageParam);
        return Result.success(result);
    }

    /** 根据ID查询租户 GET /api/tenants/{id} */
    @GetMapping("/{id}")
    public Result<Tenant> getById(@PathVariable Long id) {
        Tenant tenant = tenantService.getById(id);
        if (tenant == null) {
            return Result.notFound("租户不存在");
        }
        return Result.success(tenant);
    }

    /** 根据编码查询租户 GET /api/tenants/code/{code} */
    @GetMapping("/code/{code}")
    public Result<Tenant> getByCode(@PathVariable String code) {
        Tenant tenant = tenantService.getByCode(code);
        if (tenant == null) {
            return Result.notFound("租户不存在");
        }
        return Result.success(tenant);
    }

    /** 创建租户 POST /api/tenants */
    @PostMapping
    public Result<Tenant> create(@RequestBody Tenant tenant) {
        // 必填字段校验
        if (tenant.getName() == null || tenant.getName().isBlank()) {
            return Result.badRequest("租户名称不能为空");
        }
        if (tenant.getCode() == null || tenant.getCode().isBlank()) {
            return Result.badRequest("租户编码不能为空");
        }
        // 检查编码是否重复
        Tenant existing = tenantService.getByCode(tenant.getCode());
        if (existing != null) {
            return Result.badRequest("租户编码已存在");
        }
        // 创建租户并自动初始化默认角色
        Tenant created = tenantService.createTenant(tenant);
        return Result.success("租户创建成功（已自动创建ADMIN、USER角色）", created);
    }

    /** 更新租户 PUT /api/tenants/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        boolean success = tenantService.updateById(tenant);
        if (!success) {
            return Result.error("更新失败");
        }
        return Result.success();
    }

    /** 删除租户 DELETE /api/tenants/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean success = tenantService.removeById(id);
        if (!success) {
            return Result.error("删除失败");
        }
        return Result.success();
    }
}
