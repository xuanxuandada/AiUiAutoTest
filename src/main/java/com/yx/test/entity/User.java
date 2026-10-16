package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 密码。数据库里存的是 BCrypt 加盐哈希（形如 $2a$10$...），不是明文。
     *
     * <p>WRITE_ONLY 的含义是「只准写进来，不准发出去」：
     * <ul>
     *   <li>注册/改密时前端传的 password 还能被正常解析进来（可写）</li>
     *   <li>但任何返回 User 的接口（如 GET /api/users）在序列化成 JSON 时，
     *       这个字段会被直接丢掉，永远不会外泄（不可读）</li>
     * </ul>
     * 它只拦截「JSON 输出」，不影响数据库查询，也不影响登录校验。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    
    private String name;

    private String phone;

    private Integer age;

    @TableField("tenant_id")
    private Long tenantId;  // 租户ID（用于多租户隔离）

    @TableField("role_id")
    private Long roleId;      // 角色ID（如：1-管理员、2-普通用户）

    /**
     * 直属上级的用户ID（NULL=没有上级）。
     * 作用：数据可见范围——「我」只能看到自己创建的用例/套件/报告，
     * 但如果某条数据是「我的下级」创建的，我也能看到。
     * 下级关系就是通过这个字段串起来的（链式：上级的上级也算）。
     * 注意：雪花ID是19位，超过JS的Number.MAX_SAFE_INTEGER，必须转成字符串再给前端。
     */
    @TableField("leader_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
