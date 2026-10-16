package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 套件-用例关联（多对多中间表，含套件内副本字段）
 */
@Data
@TableName(value = "suite_case", autoResultMap = true)
public class SuiteCase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 套件ID */
    private Long suiteId;

    /** 用例ID */
    private Long caseId;

    /** 排序号（用例在套件中的执行顺序） */
    private Integer sortOrder;

    /**
     * 套件内副本步骤（NULL=继承父用例）
     * 自然语言步骤（无action）保存后需AI转换到 suite_case_script
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> steps;

    /** 套件内副本预期结果（NULL=继承父用例） */
    private String expectedResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}

