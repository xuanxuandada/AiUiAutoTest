package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "test_execution", autoResultMap = true)
public class TestExecution {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long testCaseId;

    private Long suiteId;

    /** 批次ID，用于关联同一次批量执行的记录 */
    private String batchId;

    /** PENDING-等待, RUNNING-执行中, PASSED-通过, FAILED-失败, ERROR-异常 */
    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 执行耗时(毫秒) */
    private Long durationMs;

    /** 执行结果详情 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultDetail;

    private String errorMessage;

    private String screenshotUrl;

    /** 测试报告URL（OSS地址） */
    private String reportUrl;

    /** 执行者: SYSTEM/MANUAL */
    private String executor;

    /**
     * 归属用户ID（这次执行/报告算在谁名下）。
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
