package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.User;

public interface UserService extends IService<User> {
    
    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码（明文）
     * @return 登录成功的用户对象（包含id、tenantId、roleId），失败返回null
     */
    User login(String username, String password);

    /**
     * 根据用户名查询用户信息
     * @param name 用户名
     * @return 用户对象（包含tenantId、roleId、age等），不存在返回null
     */
    User getByUsername(String name);

    User getByPhone(String phone);
}
