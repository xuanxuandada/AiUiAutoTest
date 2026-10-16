package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.Tenant;

public interface TenantService extends IService<Tenant> {

    /**
     * 根据租户编码查询租户
     * @param code 租户编码
     * @return 租户对象，不存在返回null
     */
    Tenant getByCode(String code);

    /**
     * 创建租户并自动初始化默认角色（ADMIN、USER）
     * @param tenant 租户信息（name、code必填）
     * @return 创建成功的租户（含id）
     */
    Tenant createTenant(Tenant tenant);
}
