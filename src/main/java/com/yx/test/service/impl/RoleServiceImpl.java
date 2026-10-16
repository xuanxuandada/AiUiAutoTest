package com.yx.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.Role;
import com.yx.test.mapper.RoleMapper;
import com.yx.test.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Override
    public List<Role> listByTenantId(Long tenantId) {
        return this.lambdaQuery()
                .eq(Role::getTenantId, tenantId)
                .list();
    }

    @Override
    public Role getByTenantIdAndCode(Long tenantId, String code) {
        return this.lambdaQuery()
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getCode, code)
                .one();
    }
}
