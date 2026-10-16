package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.dto.UserAiConfigRequest;
import com.yx.test.entity.UserAiConfig;

/**
 * 用户AI配置服务
 *
 * <p>核心职责：把「当前登录用户的 API Key」解析成一份运行时配置，
 * 供 AI 用例生成（Java 侧）和 Midscene 浏览器执行（Node 侧）使用。</p>
 *
 * <p><b>硬性规则：必须使用用户自己的 Key，没有全局兜底。</b>
 * 所以 {@code requireForCurrentUser()} 是 AI 功能入口的推荐用法：
 * 用户没配 Key 就当场报错，而不是悄悄用别人的 Key 把活干了。</p>
 */
public interface UserAiConfigService extends IService<UserAiConfig> {

    /** 查询某个用户的配置（不存在返回 null） */
    UserAiConfig getByUserId(Long userId);

    /** 保存/更新某个用户的配置；request 中 apiKey 留空表示保留原 Key 不变 */
    UserAiConfig saveForUser(Long userId, UserAiConfigRequest request);

    /** 删除某个用户的配置（物理删除） */
    void removeByUserId(Long userId);

    /**
     * 解析指定用户的运行时 AI 配置。
     *
     * <p>不会抛异常：如果 userId 为 null、或该用户没配 Key，
     * 返回的对象里 {@code apiKey} 为 null（{@code hasApiKey()} 为 false）。</p>
     *
     * <p>适用场景：只想要个模型名记元数据、或者做「当前生效配置」排障展示。</p>
     */
    AiRuntimeConfig resolveForUser(Long userId);

    /** 解析「当前登录用户」的运行时 AI 配置（内部读 TenantContext，不抛异常） */
    AiRuntimeConfig resolveForCurrentUser();

    /**
     * 解析「当前登录用户」的运行时 AI 配置，并且<b>要求必须可用</b>。
     *
     * <p>只要用户没配自己的 Key（或拿不到登录身份），就直接抛 BizException，
     * 由 GlobalExceptionHandler 转成友好提示返回给前端。</p>
     *
     * <p><b>凡是真正要调 AI 的地方都必须用这个方法。</b></p>
     */
    AiRuntimeConfig requireForCurrentUser();
}
