package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 套件-用例AI转换脚本（套件内副本的Midscene.js可执行步骤，独立于父用例脚本）
 * 与 (suite_id, case_id) 联合唯一
 */
@Data
@TableName(value = "suite_case_script", autoResultMap = true)
public class SuiteCaseScript {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 套件ID */
    private Long suiteId;

    /** 用例ID */
    private Long caseId;

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

