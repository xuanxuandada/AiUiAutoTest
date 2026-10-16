package com.yx.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「用户自己的 AI 配置」查询结果（脱敏后的视图对象）
 *
 * <p>对应接口：GET /api/user/ai-config</p>
 *
 * <p><b>这里永远不会返回真实 Key</b>，只返回「是否已配置」以及脱敏串（如 ****abcd）。
 * 这是接口层的硬性约束：Key 一旦离开服务器就失去了控制。</p>
 */
@Data
public class UserAiConfigVO {

    /** 脱敏后的 Key，如 ****abcd；未配置时为 null */
    private String apiKeyMasked;

    /** 是否已经配置过 Key */
    private Boolean apiKeyConfigured;

    /** AI接口基址（不含 /chat/completions） */
    private String baseUrl;

    /** 模型名称 */
    private String modelName;

    /** Midscene 模型族 */
    private String modelFamily;

    /** 超时秒数 */
    private Integer timeout;

    /** 是否启用: 1-启用, 0-停用 */
    private Integer enabled;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}
