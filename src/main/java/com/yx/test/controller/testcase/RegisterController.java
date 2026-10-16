package com.yx.test.controller.testcase;

import com.yx.test.common.Result;
import com.yx.test.entity.User;
import com.yx.test.service.UserService;
import com.yx.test.util.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册接口 (RESTful)
 */
@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegisterController {
    private final UserService userService;

    @PostMapping("")
    public Result<Void> register(@RequestBody User user) {
        // 1. 验证用户名是否重复
        User username = userService.getByUsername(user.getName());

        if (username != null) {
            return Result.error("用户名重复");
        }
        // 2. 验证手机号是否重复
        User phone = userService.getByPhone(user.getPhone());

        if (phone != null) {
            return Result.error("手机号重复");
        }

        // 3. 密码必须加密后再入库。
        //    注册接口是匿名可调的（WebMvcConfig 里放行了 /api/register），
        //    如果这里直接 save 明文密码，数据库里立刻就会出现明文记录。
        //    只做加密、不改任何返回结构，前端无需调整。
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(PasswordUtils.encode(user.getPassword()));
        }

        userService.save(user);
        return Result.success();
    }

}
