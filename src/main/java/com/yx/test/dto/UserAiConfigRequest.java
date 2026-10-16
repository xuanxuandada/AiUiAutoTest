package com.yx.test.dto;

import lombok.Data;

/**
 * 保存/更新「用户自己的 AI 配置」的请求体
 *
 * <p>对应接口：PUT /api/user/ai-config</p>
 *
 * <p>关于 apiKey 的一个约定：<b>留空表示「保持原 Key 不变」</b>。
 * 原因是查询接口只会返回脱敏后的 Key（如 ****abcd），
 * 用户如果只想改模型名而不重新输入 Key，前端提交上来的就是脱敏值或空值，
 * 此时若直接覆盖入库，真实 Key 就被破坏了。所以留空时保留库里原来的值。</p>
 */
@Data
public class UserAiConfigRequest {

    /** AI API Key。留空 = 保留原 Key 不变 */
    private String apiKey;

    /** AI接口基址，如 https://dashscope.aliyuncs.com/compatible-mode/v1（可带 /chat/completions，后端会自动归一化） */
    private String baseUrl;

    /** 模型名称，如 qwen-vl-max */
    private String modelName;

    /** Midscene 模型族，如 qwen2.5-vl（AI 浏览器自动化必填） */
    private String modelFamily;

    /** 超时秒数，缺省 60 */
    private Integer timeout;

    /** 是否启用: 1-启用, 0-停用，缺省 1 */
    private Integer enabled;
}
