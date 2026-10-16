package com.yx.test.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户AI配置实体
 *
 * <p>每个登录用户对应一条记录，保存「自己的」大模型 API Key 等配置。
 * 这样当 A 用户触发 AI 用例生成 / 回归执行时，后端只会拿 A 自己的 Key 去调用大模型，
 * 不会用别人的 Key，也不需要把 Key 写在 application.yml 里。</p>
 *
 * <p>注意：本表刻意不做逻辑删除（没有 deleted 字段）。
 * 因为一个用户只允许一条配置，而 user_id 上建了唯一索引，
 * 若同时用逻辑删除，删除后再次新增会因为旧记录仍占用唯一索引而报错。
 * 删除时直接物理删除即可。</p>
 */
@Data
@TableName("user_ai_config")
public class UserAiConfig {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 用户ID（一个用户仅一条有效配置） */
    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * AI API Key（用户自己的密钥）
     *
     * <p>WRITE_ONLY 表示「只允许反序列化（写入），不允许序列化（输出）」。
     * 这是一道保险：即使哪天有人图省事把本实体直接 return 给前端，
     * Jackson 也不会把 Key 输出出去，避免密钥泄漏。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /** AI接口基址，如 https://dashscope.aliyuncs.com/compatible-mode/v1（不含 /chat/completions） */
    @TableField("base_url")
    private String baseUrl;

    /** 模型名称，如 qwen-vl-max */
    @TableField("model_name")
    private String modelName;

    /** Midscene 模型族，如 qwen2.5-vl（浏览器自动化必填） */
    @TableField("model_family")
    private String modelFamily;

    /** 超时秒数 */
    private Integer timeout;

    /** 是否启用: 1-启用, 0-停用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
