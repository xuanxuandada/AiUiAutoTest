package com.yx.test.controller.testcase;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.test.common.BizException;
import com.yx.test.common.Result;
import com.yx.test.entity.User;
import com.yx.test.service.UserService;
import com.yx.test.util.PasswordUtils;
import com.yx.test.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理接口 (RESTful)
 *
 * <p>权限约定：用户管理属于管理员职能，「列出全部用户 / 搜索 / 新增 / 修改 / 删除」只允许管理员调用。
 * 若不限制，任何登录用户都能改别人的密码、删除别人的账号，等于没有权限体系。
 * 普通用户只允许查询自己的信息。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /** role_id 的约定值：1-管理员、2-普通用户（见 user 表字段注释） */
    private static final Long ROLE_ID_ADMIN = 1L;

    private final UserService userService;

    /** 分页查询所有用户 GET /api/users?page=1&pageSize=20 */
    @GetMapping
    public Result<Page<User>> list(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        requireAdmin();
        // 原来的 userService.list() 没写排序，实际返回的就是主键顺序，
        // 这里显式写上 id 升序：一是和原来看到的一致，二是分页翻页必须有确定的排序，
        // 否则可能出现「第 1 页和第 2 页有同一个用户」。
        return Result.success(userService.lambdaQuery()
                .orderByAsc(User::getId)
                .page(new Page<>(page, pageSize)));
    }

    /**
     * 分页用户搜索 GET /api/users/search?keyword=xxx&page=1&pageSize=20
     * 前端传一个keyword，后端自动搜索用户名和手机号（OR关系，任一匹配即返回）
     */
    @GetMapping("/search")
    public Result<Page<User>> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        requireAdmin();
        Page<User> pageParam = new Page<>(page, pageSize);

        // 关键词为空时按「全部用户」处理，直接走分页，不用再写一遍全量查询
        if (keyword == null || keyword.isBlank()) {
            return Result.success(userService.lambdaQuery()
                    .orderByAsc(User::getId)
                    .page(pageParam));
        }

        // 使用OR条件搜索，用户名/手机号任一匹配即返回。
        // 注意 or() 是把「前面已有的条件」用 OR 串起来，所以如果有数据可见范围之类的
        // 条件，必须加在 or() 之前并用括号包住，否则会被一起 OR 掉。
        // 这里是管理员专用接口，没有额外可见范围条件，所以 or() 这样写是安全的。
        // orderByAsc 不能省：分页翻页需要确定的顺序。
        return Result.success(userService.lambdaQuery()
                .like(User::getName, keyword)
                .or()
                .like(User::getPhone, keyword)
                .orderByAsc(User::getId)
                .page(pageParam));
    }

    /** 根据ID查询用户 GET /api/users/{id} */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        // 管理员能看任何人；普通用户只能看自己（查看个人资料是正常需求，不该一刀切禁掉）
        if (!isAdmin() && !id.equals(TenantContext.getCurrentUserId())) {
            throw BizException.forbidden("无权查看其它用户的信息");
        }
        User user = userService.getById(id);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        return Result.success(user);
    }

    /** 创建用户 POST /api/users */
    @PostMapping
    public Result<User> create(@RequestBody User user) {
        requireAdmin();
        // 密码必须先加密再入库，否则库里又会冒出明文密码
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(PasswordUtils.encode(user.getPassword()));
        }
        userService.save(user);
        return Result.success("用户创建成功", user);
    }

    /** 更新用户 PUT /api/users/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody User user) {
        requireAdmin();
        user.setId(id);
        // 密码处理：只有前端明确传了新密码才加密覆盖；
        // 没传就置 null，让 MyBatis-Plus 跳过这一列。
        // 否则会把库里已有的哈希再加密一次，用户就永远登录不进去了（哈希套哈希，比对必失败）
        String rawPassword = user.getPassword();
        user.setPassword((rawPassword == null || rawPassword.isBlank()) ? null : PasswordUtils.encode(rawPassword));
        boolean success = userService.updateById(user);
        if (!success) {
            return Result.error("更新失败");
        }
        return Result.success();
    }

    /** 删除用户 DELETE /api/users/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        boolean success = userService.removeById(id);
        if (!success) {
            return Result.error("删除失败");
        }
        return Result.success();
    }

    /**
     * 根据用户名查询用户信息 GET /api/users/message?username=张三
     * 返回: tenantId、roleId、age
     */
    @GetMapping("/message")
    public Result<Map<String, Object>> getUserMessage(@RequestParam String name) {
        User user = userService.getByUsername(name);

        if (user == null) {
            return Result.notFound("用户不存在");
        }

        Map<String, Object> data = Map.of(
            "tenantId", user.getTenantId(),
            "roleId", user.getRoleId(),
            "age", user.getAge()
        );

        return Result.success(data);
    }

    // ==================== 权限判断 ====================

    /** 当前登录的人是不是管理员 */
    private boolean isAdmin() {
        return ROLE_ID_ADMIN.equals(TenantContext.getCurrentRoleId());
    }

    /** 不是管理员就直接拒绝（抛业务异常，由 GlobalExceptionHandler 统一转成 403 响应） */
    private void requireAdmin() {
        if (!isAdmin()) {
            throw BizException.forbidden("该操作仅管理员可用");
        }
    }
}
