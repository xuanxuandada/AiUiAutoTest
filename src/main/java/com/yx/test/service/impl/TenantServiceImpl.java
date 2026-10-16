package com.yx.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.Role;
import com.yx.test.entity.Tenant;
import com.yx.test.mapper.TenantMapper;
import com.yx.test.service.RoleService;
import com.yx.test.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    private final RoleService roleService;

    @Override
    public Tenant getByCode(String code) {
        return this.lambdaQuery()
                .eq(Tenant::getCode, code)
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tenant createTenant(Tenant tenant) {
        // 0. 检查编码是否重复
        Tenant existing = this.getByCode(tenant.getCode());
        if (existing != null) {
            throw new IllegalArgumentException("租户编码已存在");
        }

        // 1. 保存租户
        this.save(tenant);

        // 2. 自动创建默认角色：ADMIN
        Role adminRole = new Role();
        adminRole.setTenantId(tenant.getId());
        adminRole.setName("管理员");
        adminRole.setCode("ADMIN");
        adminRole.setDescription("租户管理员，拥有全部权限");
        adminRole.setStatus("ACTIVE");
        roleService.save(adminRole);

        // 3. 自动创建默认角色：USER
        Role userRole = new Role();
        userRole.setTenantId(tenant.getId());
        userRole.setName("普通用户");
        userRole.setCode("USER");
        userRole.setDescription("普通用户，拥有基本操作权限");
        userRole.setStatus("ACTIVE");
        roleService.save(userRole);

        return tenant;
    }
}
