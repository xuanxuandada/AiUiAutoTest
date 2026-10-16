package com.yx.test.controller.testcase;

import com.yx.test.common.Result;
import com.yx.test.entity.TestCase;
import com.yx.test.service.AiGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI测试用例生成接口 (RESTful)
 */
@RestController
@RequestMapping("/api/ai-generation")
@RequiredArgsConstructor
public class AiGenerationController {

    private final AiGenerationService aiGenerationService;

    /**
     * 根据自然语言描述生成测试用例
     * POST /api/ai-generation/generate
     * Body: {"prompt": "测试用户登录功能", "targetUrl": "http://example.com/login"}
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        String targetUrl = request.get("targetUrl");

        List<TestCase> cases = aiGenerationService.generateTestCases(prompt, targetUrl);

        Map<String, Object> data = Map.of(
                "count", cases.size(),
                "cases", cases
        );
        return Result.success(data);
    }

    /**
     * 根据页面描述生成完整测试场景
     * POST /api/ai-generation/scenario
     * Body: {"pageDescription": "用户管理页面，包含增删改查", "targetUrl": "http://example.com/users", "caseCount": 5}
     */
    @PostMapping("/scenario")
    public Result<Map<String, Object>> generateScenario(@RequestBody Map<String, Object> request) {
        String pageDescription = (String) request.get("pageDescription");
        String targetUrl = (String) request.get("targetUrl");
        int caseCount = request.containsKey("caseCount") ?
                ((Number) request.get("caseCount")).intValue() : 5;

        List<TestCase> cases = aiGenerationService.generateScenario(pageDescription, targetUrl, caseCount);

        Map<String, Object> data = Map.of(
                "count", cases.size(),
                "cases", cases
        );
        return Result.success(data);
    }
}
