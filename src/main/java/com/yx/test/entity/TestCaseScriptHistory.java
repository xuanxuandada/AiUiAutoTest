package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试用例脚本历史（编辑前自动备份）
 */
@Data
@TableName(value = "test_case_script_history", autoResultMap = true)
public class TestCaseScriptHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原脚本ID */
    private Long scriptId;

    /** 关联用例ID */
    private Long testCaseId;

    /** 备份时的脚本步骤 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> scriptSteps;

    /** 转换使用的AI模型 */
    private String modelName;

    /** 备份时的状态 */
    private String status;

    /** 备份时间 */
    private LocalDateTime backupTime;
}
