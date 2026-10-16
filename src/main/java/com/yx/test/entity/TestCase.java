package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "test_case", autoResultMap = true)
public class TestCase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类ID（关联 test_case_category） */
    private Long categoryId;

    /** 分类名称（冗余字段，方便查询） */
    private String categoryName;
    private String name;

    private String originDescription;

    private String targetUrl;

    /**
     * 测试步骤，JSON格式，Midscene.js可执行
     * 示例: [{"action": "aiAction", "prompt": "点击登录按钮"}, {"action": "aiAssert", "prompt": "页面显示欢迎文字"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> steps;

    private String expectedResult;

    /** MANUAL-手动创建, AI_GENERATED-AI生成 */
    private String type;

    /** 标签，逗号分隔 */
    private String tags;

    /** 优先级: 1-最高, 5-最低 */
    private Integer priority;

    /** ACTIVE-启用, INACTIVE-禁用, DEPRECATED-废弃 */
    private String status;

    private String createdBy;

    /**
     * 归属用户ID（这条用例算在谁名下）。
     * 不需要接口传值：由 MyMetaObjectHandler 在 insert 时自动盖章为「当前登录用户」。
     * 可见规则：只有本人 + 本人的上级能看到。
     * 加 @JsonSerialize 是因为雪花ID是19位，直接当数字给前端会被JS截断精度。
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
