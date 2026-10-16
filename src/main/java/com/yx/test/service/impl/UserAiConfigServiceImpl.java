package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.common.BizException;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.dto.UserAiConfigRequest;
import com.yx.test.entity.UserAiConfig;
import com.yx.test.mapper.UserAiConfigMapper;
import com.yx.test.service.UserAiConfigService;
import com.yx.test.util.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户AI配置服务实现
 *
 * <p><b>核心规则：AI API Key 只能来自「当前登录用户自己」的配置，不存在全局兜底 Key。</b>
 * 换句话说，用户没配 Key 就用不了 AI 功能，绝不会悄悄借用别人的、或全员的 Key。</p>
 *
 * <p>为什么不做全局兜底？因为只要留了一条「兜底」的路，隔离就不再是硬约束，
 * 而是「大部分人走对路」而已 —— 一旦有人漏传/漏判，就会静默地用上全局 Key。</p>
 *
 * <p>{@code ai.api.*} 里剩下的几项（接口地址 / 模型名 / 模型族 / 超时）
 * <b>都不是密钥</b>，只作为「新建配置时的默认模板」，让用户只需粘贴一把 Key 即可。</p>
 *
 * <p>enabled=0 的语义是「停用该用户的个人配置」，效果等同于没有配置：
 * requireForCurrentUser() 会抛业务异常，提示用户去配置页处理。</p>
 */
@Slf4j
@Service
public class UserAiConfigServiceImpl extends ServiceImpl<UserAiConfigMapper, UserAiConfig>
        implements UserAiConfigService {

    /** OpenAI 兼容接口的完整路径后缀；数据库只存基址，这里负责拼接/剥离 */
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    private static final int DEFAULT_TIMEOUT = 60;
    private static final String DEFAULT_MODEL_FAMILY = "qwen2.5-vl";

    // ============ 默认模板配置（只有非敏感项，绝不包含任何密钥） ============

    /** 默认完整接口地址，如 https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions */
    @Value("${ai.api.url:}")
    private String defaultApiUrl;

    @Value("${ai.api.model:qwen-vl-max}")
    private String defaultModel;

    /** Midscene 模型族，必须是多模态模型（qwen2.5-vl / doubao-vision / gemini ...） */
    @Value("${ai.api.model-family:qwen2.5-vl}")
    private String defaultModelFamily;

    @Value("${ai.api.timeout:60}")
    private int defaultTimeout;

    // ============================== 查询 / 保存 / 删除 ==============================

    @Override
    public UserAiConfig getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        // user_id 上有唯一索引，正常情况下最多一条；LIMIT 1 只是防御，避免 one() 抛 TooManyResults
        return this.lambdaQuery()
                .eq(UserAiConfig::getUserId, userId)
                .last("LIMIT 1")
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAiConfig saveForUser(Long userId, UserAiConfigRequest request) {
        if (userId == null) {
            throw BizException.unauthorized("未登录，请先登录");
        }
        if (request == null) {
            throw BizException.badRequest("配置内容不能为空");
        }

        UserAiConfig exist = getByUserId(userId);
        UserAiConfig entity = (exist == null) ? new UserAiConfig() : exist;
        entity.setUserId(userId);

        // Key：只有明确传了非空值才覆盖，留空表示「保持原 Key 不变」
        // （因为查询接口返回的是脱敏值，前端如果原样回传会毁掉真实 Key）
        String newKey = request.getApiKey() == null ? "" : request.getApiKey().trim();
        if (!newKey.isEmpty()) {
            entity.setApiKey(newKey);
        }

        if (request.getBaseUrl() != null) {
            entity.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        }
        if (request.getModelName() != null) {
            entity.setModelName(blankToNull(request.getModelName()));
        }
        if (request.getModelFamily() != null) {
            entity.setModelFamily(blankToNull(request.getModelFamily()));
        }
        if (request.getTimeout() != null) {
            entity.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : DEFAULT_TIMEOUT);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() == 0 ? 0 : 1);
        }

        if (entity.getId() == null) {
            // 首次新增：把没填的「非敏感项」用默认模板补齐，让用户只需要粘贴一把自己的 Key
            // 注意：这里绝不会给 apiKey 做任何填充 —— Key 必须由用户自己填。
            if (entity.getEnabled() == null) {
                entity.setEnabled(1);
            }
            if (entity.getTimeout() == null || entity.getTimeout() <= 0) {
                entity.setTimeout(defaultTimeout > 0 ? defaultTimeout : DEFAULT_TIMEOUT);
            }
            if (entity.getBaseUrl() == null) {
                entity.setBaseUrl(normalizeBaseUrl(defaultApiUrl));
            }
            if (entity.getModelName() == null) {
                entity.setModelName(blankToNull(defaultModel));
            }
            if (entity.getModelFamily() == null) {
                entity.setModelFamily(firstNonBlank(null, defaultModelFamily, DEFAULT_MODEL_FAMILY));
            }
        }

        boolean ok = saveOrUpdate(entity);
        if (!ok) {
            throw BizException.internalError("保存AI配置失败");
        }
        // 注意：日志里绝不能打印 Key 本身
        log.info("用户AI配置已保存, userId={}, 已配置Key={}, model={}, modelFamily={}",
                userId, entity.getApiKey() != null && !entity.getApiKey().isBlank(),
                entity.getModelName(), entity.getModelFamily());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        // 本表没有逻辑删除（deleted），这里是物理删除
        this.remove(new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
        log.info("用户AI配置已删除, userId={}", userId);
    }

    // ============================== 运行时配置解析 ==============================

    @Override
    public AiRuntimeConfig resolveForUser(Long userId) {
        UserAiConfig cfg = getByUserId(userId);

        // 用户的个人配置是否「可用」：存在 && 未停用 && Key 非空
        boolean userKeyUsable = cfg != null
                && (cfg.getEnabled() == null || cfg.getEnabled() != 0)
                && cfg.getApiKey() != null && !cfg.getApiKey().isBlank();

        // 1) Key：只认「这个用户自己」的 Key。没有就是 null —— 不存在全局兜底 Key。
        //    把这一条做成硬约束，隔离就不依赖任何调用方自觉。
        String apiKey = userKeyUsable ? cfg.getApiKey().trim() : null;

        // 2) 基址：用户配了就用用户的，没配就退回默认模板（非敏感，不是密钥）
        String baseUrl = (cfg == null) ? null : normalizeBaseUrl(cfg.getBaseUrl());
        if (baseUrl == null) {
            // 默认 url 是「完整地址」，这里剥掉 /chat/completions 变成基址
            baseUrl = normalizeBaseUrl(defaultApiUrl);
        }

        // 3) 模型名 / 模型族：同样是「用户配置优先，否则默认模板」
        String model = firstNonBlank(cfg == null ? null : cfg.getModelName(), defaultModel);
        String modelFamily = firstNonBlank(cfg == null ? null : cfg.getModelFamily(),
                defaultModelFamily, DEFAULT_MODEL_FAMILY);

        // 4) 超时
        int timeout = (cfg != null && cfg.getTimeout() != null && cfg.getTimeout() > 0)
                ? cfg.getTimeout()
                : (defaultTimeout > 0 ? defaultTimeout : DEFAULT_TIMEOUT);

        // 5) Java 后端要用「完整地址」，由基址派生
        String apiUrl = (baseUrl == null) ? null : baseUrl + CHAT_COMPLETIONS_SUFFIX;

        if (log.isDebugEnabled()) {
            log.debug("解析AI配置, userId={}, 使用自己的Key={}, model={}, modelFamily={}, baseUrl={}",
                    userId, userKeyUsable, model, modelFamily, baseUrl);
        }

        return new AiRuntimeConfig(baseUrl, apiUrl, apiKey, model, modelFamily, timeout);
    }

    @Override
    public AiRuntimeConfig resolveForCurrentUser() {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            // 这是最容易踩的坑：@Async 异步线程里 ThreadLocal 默认是空的。
            // 现在没有全局兜底了，拿不到用户 = 拿不到 Key，调用方一定会报错，所以这里必须告警。
            log.warn("当前线程没有登录用户信息（TenantContext 为空），解析不到任何AI配置。"
                    + "若此处出现在异步任务中，请确认 AsyncConfig 里的 TenantContextTaskDecorator 已生效");
        }
        return resolveForUser(userId);
    }

    @Override
    public AiRuntimeConfig requireForCurrentUser() {
        Long userId = TenantContext.getCurrentUserId();
        AiRuntimeConfig cfg = resolveForUser(userId);
        if (cfg.hasApiKey()) {
            return cfg;
        }
        // 区分「没拿到登录身份」和「登录了但没配 Key」，给出不同的提示
        if (userId == null) {
            throw new BizException(400,
                    "未获取到当前登录用户，无法确定该使用哪个API Key，请重新登录后再试");
        }
        // 没有全局兜底：用户必须配置自己的 Key，否则 AI 功能一律不可用
        throw new BizException(400,
                "你还没有配置自己的 AI API Key，请先到【设置 → AI模型配置】保存后再使用");
    }

    // ============================== 工具方法 ==============================

    /**
     * 把各种写法的地址统一归一化成「基址」。
     *
     * <p>为什么要做这件事：Midscene 的 MIDSCENE_MODEL_BASE_URL 要求基址
     * （如 .../compatible-mode/v1），而 Java 侧调 chat/completions 需要完整地址。
     * 用户很可能直接复制完整地址填进来，所以这里统一剥掉后缀，只存基址。</p>
     */
    static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        // 去掉尾部的斜杠
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // 剥掉 /chat/completions 后缀
        if (url.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            url = url.substring(0, url.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? null : url;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            String t = blankToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }
}
