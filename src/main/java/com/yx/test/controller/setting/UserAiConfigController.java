package com.yx.test.controller.setting;

import com.yx.test.common.BizException;
import com.yx.test.common.Result;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.dto.EffectiveAiConfigVO;
import com.yx.test.dto.UserAiConfigRequest;
import com.yx.test.dto.UserAiConfigVO;
import com.yx.test.entity.UserAiConfig;
import com.yx.test.service.UserAiConfigService;
import com.yx.test.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户AI配置接口 (RESTful)
 *
 * <p>每个登录用户通过这组接口维护「自己的」大模型 API Key，
 * 后端在执行 AI 用例生成 / Midscene 浏览器自动化时，只会使用当前登录用户的 Key。</p>
 *
 * <p>接口清单：</p>
 * <ul>
 *   <li>GET    /api/user/ai-config           查询自己的配置（Key 脱敏返回）</li>
 *   <li>PUT    /api/user/ai-config           保存/更新自己的配置</li>
 *   <li>DELETE /api/user/ai-config           删除自己的配置</li>
 *   <li>GET    /api/user/ai-config/effective 查看当前实际生效的配置来源（排障用）</li>
 * </ul>
 *
 * <p><b>注意：系统必须使用「当前登录用户自己」的 Key，没有全局兜底。</b>
 * 用户没配置时，AI 用例生成 / AI 执行都会直接提示：
 * 「你还没有配置自己的 AI API Key，请先到【设置 → AI模型配置】保存后再使用」。</p>
 *
 * <p>安全说明：用户身份完全取自 JWT（TenantContext），接口里没有任何
 * 「userId」入参，因此<b>无法通过传参去读写别人的配置</b>。</p>
 */
@RestController
@RequestMapping("/api/user/ai-config")
@RequiredArgsConstructor
public class UserAiConfigController {

    private final UserAiConfigService userAiConfigService;

    /** 查询自己的AI配置（Key 只会返回脱敏串，如 ****abcd） */
    @GetMapping
    public Result<UserAiConfigVO> get() {
        Long userId = currentUserId();
        return Result.success(toVO(userAiConfigService.getByUserId(userId)));
    }

    /** 保存/更新自己的AI配置；apiKey 留空表示保持原 Key 不变 */
    @PutMapping
    public Result<UserAiConfigVO> save(@RequestBody UserAiConfigRequest request) {
        Long userId = currentUserId();
        UserAiConfig saved = userAiConfigService.saveForUser(userId, request);
        if (saved.getApiKey() == null || saved.getApiKey().isBlank()) {
            return Result.success("已保存，但你还没有填写API Key，AI功能暂时不可用", toVO(saved));
        }
        return Result.success("保存成功", toVO(saved));
    }

    /** 删除自己的AI配置（删除后 AI 功能会立即不可用：系统不存在任何全局兜底 Key） */
    @DeleteMapping
    public Result<Void> delete() {
        userAiConfigService.removeByUserId(currentUserId());
        return Result.success("已删除你的AI配置，AI功能将不可用，请随时重新配置", null);
    }

    /**
     * 查看当前实际生效的配置来源（排障用，不返回 Key）
     *
     * <p>典型用法：AI 功能报「你还没有配置API Key」时调一下它，
     * 就能看出后端这次到底是拿谁的配置在跑。</p>
     */
    @GetMapping("/effective")
    public Result<EffectiveAiConfigVO> effective() {
        AiRuntimeConfig cfg = userAiConfigService.resolveForCurrentUser();
        // 没有全局兜底，所以只有两种情况：用了自己的 Key，或者根本没有可用的 Key
        String source = cfg.hasApiKey() ? "USER_CONFIG" : "NONE";
        return Result.success(new EffectiveAiConfigVO(
                source,
                cfg.model(),
                cfg.modelFamily(),
                cfg.baseUrl(),
                cfg.timeout(),
                cfg.hasApiKey()));
    }

    // ============================== 私有工具 ==============================

    /** 从 JWT 解析出的当前登录用户ID；拿不到就说明没登录 */
    private Long currentUserId() {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录，请先登录");
        }
        return userId;
    }

    /** 实体 → 脱敏 VO，绝不把真实 Key 交给前端 */
    private UserAiConfigVO toVO(UserAiConfig entity) {
        UserAiConfigVO vo = new UserAiConfigVO();
        if (entity == null) {
            vo.setApiKeyConfigured(false);
            return vo;
        }
        boolean configured = entity.getApiKey() != null && !entity.getApiKey().isBlank();
        vo.setApiKeyConfigured(configured);
        vo.setApiKeyMasked(configured ? maskKey(entity.getApiKey()) : null);
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModelName(entity.getModelName());
        vo.setModelFamily(entity.getModelFamily());
        vo.setTimeout(entity.getTimeout());
        vo.setEnabled(entity.getEnabled());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 只保留末 4 位，用于让用户确认「我配的是哪把 Key」 */
    private String maskKey(String apiKey) {
        String key = apiKey.trim();
        if (key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }
}
