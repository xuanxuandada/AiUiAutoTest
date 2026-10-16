package com.yx.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.User;
import com.yx.test.mapper.UserMapper;
import com.yx.test.service.UserService;
import com.yx.test.util.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    @Override
    public User login(String username, String password) {
        // 1. 根据用户名查询用户
        User user = this.lambdaQuery()
                .eq(User::getName, username)
                .one();
        
        if (user == null) {
            // 用户不存在
            return null;
        }
        
        // 2. 校验密码。库里存的是 BCrypt 哈希，不能再用 equals 直接比：
        //    BCrypt 加了随机盐，同一个密码每次加密结果都不同，
        //    必须把用户输入的明文交给 BCrypt 按同样规则算一遍再比对。
        //    PasswordUtils.matches 内部兼容了「库里还是老明文」的情况。
        if (!PasswordUtils.matches(password, user.getPassword())) {
            // 密码错误
            return null;
        }
        
        // 3. 老数据平滑升级：若这次比对走的是「明文 equals」（说明库里还没加密），
        //    就趁这次登录手上正好有明文，顺手写成 BCrypt 哈希存回去。
        //    好处：不用停机刷数据，老用户只要登录一次就自动完成迁移。
        if (!PasswordUtils.isEncoded(user.getPassword())) {
            try {
                String encoded = PasswordUtils.encode(password);
                User patch = new User();
                patch.setId(user.getId());
                patch.setPassword(encoded);
                this.updateById(patch);    // 只更新密码这一列，不会覆盖其它字段
                user.setPassword(encoded); // 同步内存对象，避免明文继续往外传
            } catch (Exception e) {
                // 升级只是「锦上添花」，绝不能因为它失败就让用户登不进来（下次登录会自动重试）
                log.warn("明文密码自动升级为BCrypt失败, userId={}", user.getId(), e);
            }
        }
        
        // 4. 登录成功，返回用户对象（包含 id、tenantId、roleId）
        return user;
    }

    @Override
    public User getByUsername(String name) {
        return this.lambdaQuery()
                .eq(User::getName, name)
                .one();
    }

    @Override
    public User getByPhone(String phone) {
        return this.lambdaQuery()
                .eq(User::getPhone, phone)
                .one();
    }
}
