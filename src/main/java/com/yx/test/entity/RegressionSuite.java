package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("regression_suite")
public class RegressionSuite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String description;

    /** 登录态提取命令 */
    private String loginFetchCommand;

    /** 是否启用登录态: 1-启用, 0-禁用 */
    private Integer loginEnabled;

    /** 关联的用例ID列表（非数据库字段，从suite_case关联表查询填充） */
    @TableField(exist = false)
    private List<Long> caseIds;

    /** 定时执行Cron表达式 */
    private String scheduleCron;

    /** ACTIVE-启用, INACTIVE-禁用 */
    private String status;

    /**
     * 归属用户ID（这个套件算在谁名下）。
     * 不需要接口传值：由 MyMetaObjectHandler 在 insert 时自动盖章为「当前登录用户」。
     * 可见规则：只有本人 + 本人的上级能看到。
     * 加 @JsonSerialize 是因为雪花ID是19位，直接当数字给前端会被JS截断精度。
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private LocalDateTime lastRunTime;

    private String lastRunResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
