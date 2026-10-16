package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试用例脚本（AI转换后的Midscene.js可执行步骤）
 * 与 test_case 一对一关系
 */
@Data
@TableName(value = "test_case_script", autoResultMap = true)
public class TestCaseScript {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联用例ID */
    private Long testCaseId;

    /** AI转换后的Midscene.js可执行步骤 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> scriptSteps;

    /** 转换使用的AI模型 */
    private String modelName;

    /** PENDING-待转换, CONVERTED-已转换, FAILED-转换失败 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
