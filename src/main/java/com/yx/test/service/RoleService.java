package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.Role;

import java.util.List;

public interface RoleService extends IService<Role> {

    /**
     * 根据租户ID查询角色列表
     * @param tenantId 租户ID
     * @return 角色列表
     */
    List<Role> listByTenantId(Long tenantId);

    /**
     * 根据租户ID和角色编码查询角色
     * @param tenantId 租户ID
     * @param code 角色编码
     * @return 角色对象，不存在返回null
     */
    Role getByTenantIdAndCode(Long tenantId, String code);
}
