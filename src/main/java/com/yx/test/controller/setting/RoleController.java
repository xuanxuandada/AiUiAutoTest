package com.yx.test.controller.setting;

import com.yx.test.common.Result;
import com.yx.test.entity.Role;
import com.yx.test.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理接口 (RESTful)
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** 查询所有角色 GET /api/roles */
    @GetMapping
    public Result<List<Role>> list() {
        return Result.success(roleService.list());
    }

    /** 根据ID查询角色 GET /api/roles/{id} */
    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return Result.notFound("角色不存在");
        }
        return Result.success(role);
    }

    /** 根据租户ID查询角色列表 GET /api/roles/tenant/{tenantId} */
    @GetMapping("/tenant/{tenantId}")
    public Result<List<Role>> listByTenantId(@PathVariable Long tenantId) {
        return Result.success(roleService.listByTenantId(tenantId));
    }

    /** 创建角色 POST /api/roles */
    @PostMapping
    public Result<Role> create(@RequestBody Role role) {
        // 必填字段校验
        if (role.getName() == null || role.getName().isBlank()) {
            return Result.badRequest("角色名称不能为空");
        }
        if (role.getCode() == null || role.getCode().isBlank()) {
            return Result.badRequest("角色编码不能为空");
        }
        if (role.getTenantId() == null) {
            return Result.badRequest("租户ID不能为空");
        }
        // 检查同租户下编码是否重复
        Role existing = roleService.getByTenantIdAndCode(role.getTenantId(), role.getCode());
        if (existing != null) {
            return Result.badRequest("该租户下角色编码已存在");
        }
        roleService.save(role);
        return Result.success("角色创建成功", role);
    }

    /** 更新角色 PUT /api/roles/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        boolean success = roleService.updateById(role);
        if (!success) {
            return Result.error("更新失败");
        }
        return Result.success();
    }

    /** 删除角色 DELETE /api/roles/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean success = roleService.removeById(id);
        if (!success) {
            return Result.error("删除失败");
        }
        return Result.success();
    }
}
