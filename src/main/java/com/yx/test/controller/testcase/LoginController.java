package com.yx.test.controller.testcase;

import com.yx.test.common.Result;
import com.yx.test.dto.LoginRequest;
import com.yx.test.dto.LoginResponse;
import com.yx.test.dto.UpdatePasswordRequest;
import com.yx.test.entity.Tenant;
import com.yx.test.entity.User;
import com.yx.test.service.TenantService;
import com.yx.test.service.UserService;
import com.yx.test.util.JwtUtils;
import com.yx.test.util.PasswordUtils;
import com.yx.test.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 登录接口 (RESTful)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final UserService userService;
    private final TenantService tenantService;
    private final JwtUtils jwtUtils;

    /**
     * 第一步：验证用户名密码 POST /api/auth/verify
     * Body: {"name": "张三", "password": "123456"}
     * 返回: userId（用于后续步骤）
     */
    @PostMapping("/verify")
    public Result<String> verifyPassword(@RequestBody LoginRequest request) {
        User user = userService.login(request.getName(), request.getPassword());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }
        return Result.success("登录成功");
    }

    /**
     * 第二步：获取用户关联的租户列表 GET /api/auth/tenants?name=xxx&password=xxx
     * 需要再次验证密码，防止未授权访问
     */
    @GetMapping("/tenants")
    public Result<List<Map<String, Object>>> getTenantsByName(
            @RequestParam String name,
            @RequestParam String password) {
        // 1. 先验证密码是否正确
        User loginUser = userService.login(name, password);
        if (loginUser == null) {
            return Result.error("用户名或密码错误");
        }
        
        // 2. 查询该用户名对应的所有用户记录（可能属于多个租户）
        List<User> users = userService.lambdaQuery()
                .eq(User::getName, name)
                .list();
        
        // 3. 提取租户ID，查询租户信息
        List<Long> tenantIds = users.stream()
                .map(User::getTenantId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        if (tenantIds.isEmpty()) {
            return Result.success(List.of());
        }
        
        // 4. 查询租户详情，返回 id、name、code（ID 转字符串避免 JS 精度丢失）
        List<Map<String, Object>> tenants = tenantService.listByIds(tenantIds).stream()
                .map(t -> Map.<String, Object>of(
                        "id", String.valueOf(t.getId()),
                        "name", t.getName(),
                        "code", t.getCode() != null ? t.getCode() : ""
                ))
                .collect(Collectors.toList());
        
        return Result.success(tenants);
    }

    /** 
     * 第三步：正式登录生成 Token POST /api/auth/login
     * Body: {"name": "张三", "password": "123456", "tenantId": 1}
     * 返回: JWT token + 用户基本信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {

        // 1. 验证用户名和密码（再次校验，防止中间被篡改）
        User loginUser = userService.login(request.getName(), request.getPassword());
        
        if (loginUser == null) {
            return Result.error("用户名或密码错误");
        }
        
        // 2. 验证租户ID是否传入
        if (request.getTenantId() == null) {
            return Result.error("请选择租户");
        }
        
        // 3. 验证用户是否属于所选租户（关键安全检查）
        if (!request.getTenantId().equals(loginUser.getTenantId())) {
            return Result.error("用户不属于所选租户");
        }
        
        // 4. 验证租户状态
        Tenant tenant = tenantService.getById(loginUser.getTenantId());
        if (tenant == null) {
            return Result.error("租户不存在");
        }
        
        if (!"ACTIVE".equals(tenant.getStatus())) {
            return Result.error("租户已禁用，请联系管理员");
        }

        // 5. 生成token（包含 userId、tenantId、roleId）
        String token = jwtUtils.generateToken(
                loginUser.getId(),
                loginUser.getTenantId(),
                loginUser.getRoleId()
        );
        
        // 6. 返回token + 用户信息
        LoginResponse response = new LoginResponse(
            token,
            loginUser.getId(),
            loginUser.getTenantId(),
            loginUser.getRoleId(),
            loginUser.getName()
        );
        
        return Result.success(response);
    }

    @PutMapping("/updatePassword")
    public Result<Void> updatePassword(@RequestBody UpdatePasswordRequest request) {
        // 1. 从 TenantContext 获取当前登录用户ID（拦截器已经验证过 Token 了）
        Long userId = TenantContext.getCurrentUserId();
        // 2. 从 userid 获取用户
        User user = userService.getById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        // 3. 先校验入参：新密码为空时如果直接往下走，null 会被 MyBatis-Plus 忽略，
        //    结果「密码没变却提示修改成功」，这里先拦下来
        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.isBlank()) {
            return Result.badRequest("新密码不能为空");
        }

        // 4. 校验原密码：库里是 BCrypt 哈希，不能直接 equals，
        //    要用 PasswordUtils 比对（它兼容库里还是明文的老数据）
        if (!PasswordUtils.matches(request.getOldPassword(), user.getPassword())) {
            return Result.error("原密码错误");
        }

        // 5. 新密码必须先加密再入库，数据库里任何时刻都不应该出现明文密码。
        //    这里只构造「带 id + 新密码」的对象去更新，避免把整条查出来的记录回写覆盖别的字段。
        User patch = new User();
        patch.setId(user.getId());
        patch.setPassword(PasswordUtils.encode(newPassword));
        userService.updateById(patch);
        return Result.success("密码更新成功",null);
    }
}




