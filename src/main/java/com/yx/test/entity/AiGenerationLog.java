package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "ai_generation_log", autoResultMap = true)
public class AiGenerationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String prompt;

    private String aiResponse;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> generatedCaseIds;

    private String modelName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
