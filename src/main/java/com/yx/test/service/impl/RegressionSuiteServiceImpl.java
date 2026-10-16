package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.common.BizException;
import com.yx.test.dto.LoginFetchCommandRequest;
import com.yx.test.entity.RegressionSuite;
import com.yx.test.entity.SuiteCase;
import com.yx.test.entity.SuiteCaseScript;
import com.yx.test.entity.TestCase;
import com.yx.test.mapper.RegressionSuiteMapper;
import com.yx.test.mapper.SuiteCaseMapper;
import com.yx.test.mapper.SuiteCaseScriptMapper;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.RegressionSuiteService;
import com.yx.test.service.SuiteCaseScriptService;
import com.yx.test.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 回归测试套件服务实现
 * 套件-用例关联采用增量同步（保留副本字段），新加入用例时自动准备套件副本脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegressionSuiteServiceImpl extends ServiceImpl<RegressionSuiteMapper, RegressionSuite> implements RegressionSuiteService {

    private final SuiteCaseMapper suiteCaseMapper;
    private final SuiteCaseScriptMapper suiteCaseScriptMapper;
    private final SuiteCaseScriptService suiteCaseScriptService;
    private final TestCaseService testCaseService;

    /** 数据可见范围：管「谁的套件我能看/能改」 */
    private final DataScopeService dataScopeService;

    /**
     * 校验套件归属：不是我的、也不是我下级创建的，直接 403。
     * 所有「传了 suiteId 就能动数据」的方法都应该先调它。
     */
    private void assertSuiteAccessible(Long suiteId) {
        RegressionSuite suite = getById(suiteId);
        if (suite == null) {
            throw BizException.notFound("套件不存在");
        }
        dataScopeService.assertCanAccess(suite.getUserId());
    }

    @Override
    public Page<RegressionSuite> listWithCaseIds(String keyword, Long id, long page, long pageSize) {
        // 只查出「我 + 我的下级」创建的套件。
        // 先分页、再补 caseIds：原来是对全表套件逐个查关联表，套件一多就是 N 次查询；
        // 现在只给「当前这一页」的套件补，查询次数直接降下来了。
        // 排序必须留着，否则翻页会出现重复/漏掉。
        LambdaQueryWrapper<RegressionSuite> wrapper = new LambdaQueryWrapper<RegressionSuite>()
                .in(RegressionSuite::getUserId, dataScopeService.visibleUserIds());

        // 页面上「ID」输入框：精确匹配（与名称同时传时为 AND）
        if (id != null) {
            wrapper.eq(RegressionSuite::getId, id);
        }

        // 页面上「套件名称」输入框：名称模糊匹配；
        // 关键字是纯数字时再额外按 ID 精确匹一下（一个框同时兼容「搜名称」和「搜 ID」）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            Long idKeyword = parseLongOrNull(kw);
            wrapper.and(w -> {
                w.like(RegressionSuite::getName, kw);
                if (idKeyword != null) {
                    w.or().eq(RegressionSuite::getId, idKeyword);
                }
            });
        }

        wrapper.orderByAsc(RegressionSuite::getId);
        Page<RegressionSuite> result = page(new Page<>(page, pageSize), wrapper);
        for (RegressionSuite suite : result.getRecords()) {
            suite.setCaseIds(getCaseIdsBySuiteId(suite.getId()));
        }
        return result;
    }

    /** 关键字能解析成 Long 才按 ID 匹配；解析不了（比如带中文）就只按名称模糊搜 */
    private Long parseLongOrNull(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public RegressionSuite getSuiteWithCaseIds(Long id) {
        RegressionSuite suite = getById(id);
        if (suite != null) {
            // 不是我的套件就别想靠猜 ID 偷看
            dataScopeService.assertCanAccess(suite.getUserId());
            suite.setCaseIds(getCaseIdsBySuiteId(id));
        }
        return suite;
    }

    @Override
    @Transactional
    public RegressionSuite saveSuiteWithCases(RegressionSuite suite) {
        // 防伪造：前端就算传了 userId 也抹掉，由 MyMetaObjectHandler 盖章为当前登录用户
        suite.setUserId(null);
        save(suite);
        syncCaseIds(suite.getId(), suite.getCaseIds());
        return suite;
    }

    @Override
    @Transactional
    public void updateSuiteWithCases(Long id, RegressionSuite suite) {
        assertSuiteAccessible(id);

        suite.setId(id);
        suite.setUserId(null); // 不允许「过户」给别的归属人
        updateById(suite);
        if (suite.getCaseIds() != null) {
            syncCaseIds(id, suite.getCaseIds());
        }
    }

    @Override
    @Transactional
    public void deleteSuiteWithCases(Long id) {
        assertSuiteAccessible(id);

        removeById(id);
        suiteCaseMapper.physicalDeleteBySuiteId(id);
        suiteCaseScriptMapper.physicalDeleteBySuiteId(id);
    }

    @Override
    public List<Long> getCaseIdsBySuiteId(Long suiteId) {
        List<SuiteCase> suiteCases = suiteCaseMapper.selectList(
                new LambdaQueryWrapper<SuiteCase>()
                        .eq(SuiteCase::getSuiteId, suiteId)
                        .orderByAsc(SuiteCase::getSortOrder));
        List<Long> caseIds = new ArrayList<>();
        for (SuiteCase sc : suiteCases) {
            caseIds.add(sc.getCaseId());
        }
        return caseIds;
    }

    @Override
    public void removeCaseFromSuites(Long caseId) {
        suiteCaseMapper.physicalDeleteByCaseId(caseId);
    }

    @Override
    public void loginFetchCommand(Long id, LoginFetchCommandRequest loginFetch) {
        assertSuiteAccessible(id);
        // 禁用登录态(0)时，清空该行的登录态提取命令
        String logintext = loginFetch.getLogintext();
        if (loginFetch.getLoginEnabled() != null && loginFetch.getLoginEnabled() == 0) {
            logintext = null;
        }
        suiteCaseMapper.updateLoginFetchCommandById(id, logintext, loginFetch.getLoginEnabled());
    }

    @Override
    public List<Map<String, Object>> getSuiteCasesDetail(Long suiteId) {
        assertSuiteAccessible(suiteId);
        List<SuiteCase> suiteCases = suiteCaseMapper.selectList(
                new LambdaQueryWrapper<SuiteCase>()
                        .eq(SuiteCase::getSuiteId, suiteId)
                        .orderByAsc(SuiteCase::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SuiteCase sc : suiteCases) {
            TestCase tc = testCaseService.getById(sc.getCaseId());
            if (tc == null || !"ACTIVE".equals(tc.getStatus())) {
                continue; // 无效/未启用用例不返回
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("caseId", sc.getCaseId());
            item.put("name", tc.getName());
            item.put("targetUrl", tc.getTargetUrl());
            item.put("priority", tc.getPriority());
            item.put("categoryName", tc.getCategoryName());
            item.put("type", tc.getType());
            item.put("status", tc.getStatus());
            // 合并副本覆盖：副本steps优先（副本缺expected时从父用例对应步骤补齐），NULL继承父用例
            List<Map<String, String>> mergedSteps = mergeSteps(sc, tc);
            item.put("steps", mergedSteps);
            // 预期结果：副本级 > 用例级 > 步骤级(expected字段)拼接
            item.put("expectedResult", resolveExpectedResult(mergedSteps, sc, tc));
            item.put("hasCopy", sc.getSteps() != null || sc.getExpectedResult() != null);
            // 套件副本脚本状态
            SuiteCaseScript script = suiteCaseScriptService.getBySuiteAndCase(suiteId, sc.getCaseId());
            item.put("copyStatus", script != null ? script.getStatus() : null);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateSuiteCaseCopy(Long suiteId, Long caseId,
                                    List<Map<String, String>> steps,
                                    String expectedResult) {
        assertSuiteAccessible(suiteId);

        // 1. upsert 关联记录（保存副本字段，不覆盖父用例）
        SuiteCase sc = suiteCaseMapper.selectOne(new LambdaQueryWrapper<SuiteCase>()
                .eq(SuiteCase::getSuiteId, suiteId)
                .eq(SuiteCase::getCaseId, caseId));
        if (sc == null) {
            sc = new SuiteCase();
            sc.setSuiteId(suiteId);
            sc.setCaseId(caseId);
            sc.setSortOrder(getNextSortOrder(suiteId));
            suiteCaseMapper.insert(sc);
        }

        // steps 为 null 时表示清空副本：将副本字段置NULL并删除套件副本脚本（恢复继承父用例）
        if (steps == null) {
            suiteCaseMapper.update(null, new LambdaUpdateWrapper<SuiteCase>()
                    .eq(SuiteCase::getId, sc.getId())
                    .set(SuiteCase::getSteps, null)
                    .set(SuiteCase::getExpectedResult, null));
            suiteCaseScriptService.removeSuiteCaseScript(suiteId, caseId);
            return;
        }

        sc.setSteps(steps);
        sc.setExpectedResult(expectedResult);
        suiteCaseMapper.updateById(sc);

        // 2. 触发套件副本脚本转换（自然语言才调AI；Midscene格式直接落库）
        TestCase tc = testCaseService.getById(caseId);
        String targetUrl = tc != null ? tc.getTargetUrl() : null;
        if (steps.isEmpty()) {
            // 空步骤：删除套件副本脚本，执行时回退父用例
            suiteCaseScriptService.removeSuiteCaseScript(suiteId, caseId);
            return;
        }
        boolean needsClassify = steps.stream().anyMatch(s -> !s.containsKey("action"));
        if (needsClassify) {
            // 重置/创建PENDING记录后异步AI转换
            SuiteCaseScript script = suiteCaseScriptService.getBySuiteAndCase(suiteId, caseId);
            if (script == null) {
                script = new SuiteCaseScript();
                script.setSuiteId(suiteId);
                script.setCaseId(caseId);
                suiteCaseScriptService.save(script);
            } else {
                script.setScriptSteps(List.of());
                script.setStatus("PENDING");
                suiteCaseScriptService.updateById(script);
            }
            suiteCaseScriptService.convertSuiteStepsAsync(suiteId, caseId, steps, targetUrl, true);
        } else {
            // 已是Midscene格式，无需AI
            suiteCaseScriptService.saveConvertedScript(suiteId, caseId, steps, null);
        }
    }

    @Override
    @Transactional
    public void removeSuiteCase(Long suiteId, Long caseId) {
        assertSuiteAccessible(suiteId);
        suiteCaseMapper.physicalDeleteBySuiteCase(suiteId, caseId);
        suiteCaseScriptService.removeSuiteCaseScript(suiteId, caseId);
    }

    /**
     * 增量同步套件-用例关联：保留已有记录（含副本字段），仅增删和调整排序
     * 新加入的用例自动准备套件副本脚本（父用例未转换时触发AI转换）
     */
    private void syncCaseIds(Long suiteId, List<Long> caseIds) {
        List<SuiteCase> existingList = suiteCaseMapper.selectList(
                new LambdaQueryWrapper<SuiteCase>().eq(SuiteCase::getSuiteId, suiteId));
        Map<Long, SuiteCase> existingMap = new HashMap<>();
        for (SuiteCase sc : existingList) {
            existingMap.put(sc.getCaseId(), sc);
        }
        Set<Long> requested = new HashSet<>();
        if (caseIds != null) {
            for (int i = 0; i < caseIds.size(); i++) {
                Long cid = caseIds.get(i);
                requested.add(cid);
                SuiteCase sc = existingMap.get(cid);
                if (sc == null) {
                    // 新增关联：准备套件副本脚本
                    sc = new SuiteCase();
                    sc.setSuiteId(suiteId);
                    sc.setCaseId(cid);
                    sc.setSortOrder(i);
                    suiteCaseMapper.insert(sc);
                    suiteCaseScriptService.ensureSuiteCaseScript(suiteId, cid);
                } else if (sc.getSortOrder() == null || sc.getSortOrder() != i) {
                    sc.setSortOrder(i);
                    suiteCaseMapper.updateById(sc);
                }
            }
        }
        // 移除已不再关联的
        for (SuiteCase sc : existingList) {
            if (!requested.contains(sc.getCaseId())) {
                suiteCaseMapper.physicalDeleteBySuiteCase(suiteId, sc.getCaseId());
                suiteCaseScriptService.removeSuiteCaseScript(suiteId, sc.getCaseId());
            }
        }
    }

    private int getNextSortOrder(Long suiteId) {
        List<SuiteCase> list = suiteCaseMapper.selectList(
                new LambdaQueryWrapper<SuiteCase>()
                        .eq(SuiteCase::getSuiteId, suiteId)
                        .orderByDesc(SuiteCase::getSortOrder));
        if (list.isEmpty() || list.get(0).getSortOrder() == null) {
            return 0;
        }
        return list.get(0).getSortOrder() + 1;
    }

    /**
     * 合并步骤：副本步骤优先；副本步骤缺失expected字段时，从父用例对应位置补齐（保证预期结果不丢失）
     */
    private List<Map<String, String>> mergeSteps(SuiteCase sc, TestCase tc) {
        List<Map<String, String>> copySteps = sc.getSteps();
        if (copySteps == null) {
            return tc.getSteps(); // 无副本，直接继承父用例
        }
        List<Map<String, String>> parentSteps = tc.getSteps();
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < copySteps.size(); i++) {
            Map<String, String> s = new HashMap<>(copySteps.get(i));
            // 副本该步骤没有预期结果时，用父用例对应位置补齐
            String expected = s.get("expected");
            if ((expected == null || expected.isEmpty())
                    && parentSteps != null && i < parentSteps.size()) {
                String parentExpected = parentSteps.get(i).get("expected");
                if (parentExpected != null && !parentExpected.isEmpty()) {
                    s.put("expected", parentExpected);
                }
            }
            result.add(s);
        }
        return result;
    }

    /**
     * 解析可展示的预期结果：副本级 > 用例级 > 步骤级(expected字段)多行拼接
     */
    private String resolveExpectedResult(List<Map<String, String>> mergedSteps, SuiteCase sc, TestCase tc) {
        // 1. 套件副本预期结果
        if (sc.getExpectedResult() != null && !sc.getExpectedResult().isEmpty()) {
            return sc.getExpectedResult();
        }
        // 2. 父用例级预期结果
        if (tc.getExpectedResult() != null && !tc.getExpectedResult().isEmpty()) {
            return tc.getExpectedResult();
        }
        // 3. 步骤级预期结果拼接（mergedSteps[i].expected）
        if (mergedSteps != null) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, String> s : mergedSteps) {
                String e = s.get("expected");
                if (e != null && !e.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(e);
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }
}
