package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试用例分类
 */
@Data
@TableName("test_case_category")
public class TestCaseCategory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID（数据隔离） */
    private Long tenantId;

    /** 分类名称 */
    private String name;

    /** 分类编码（唯一标识，如 LOGIN_MODULE, PAYMENT_MODULE） */
    private String code;

    /** 父分类ID（0表示根分类） */
    private Long parentId;

    /** 分类描述 */
    private String description;

    /** 排序号（数字越小越靠前） */
    private Integer sortOrder;

    /** ACTIVE-启用, INACTIVE-禁用 */
    private String status;

    /** 创建者 */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;

    /** 子分类列表（非数据库字段，用于树形结构返回） */
    @TableField(exist = false)
    private List<TestCaseCategory> children;
}
