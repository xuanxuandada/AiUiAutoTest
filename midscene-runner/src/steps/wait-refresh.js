/**
 * aiWaitRefresh 步骤：周期性刷新页面并等待条件满足
 *
 * 适用场景：异步流程（如提交后状态慢慢变化，需刷新页面才会更新）
 * 解决 aiWaitFor 不刷新页面、默认超时短的问题：
 *   1. AI 断言当前页面条件是否满足
 *   2. 未满足则刷新页面（domcontentloaded 容错 SPA 跳转）
 *   3. 重复直到满足或超过总超时
 */

const { REFRESH_TOTAL_TIMEOUT_MS, REFRESH_INTERVAL_MS, NAV_TIMEOUT_MS } = require('../config');
const logger = require('../logger');

/**
 * @param {object} agent Midscene PlaywrightAgent
 * @param {import('playwright').Page} page
 * @param {object} step {prompt: 等待条件, timeout: 总超时ms, refreshInterval: 刷新间隔ms}
 * @returns {Promise<object>} 步骤执行结果
 */
async function handleWaitRefresh(agent, page, step) {
    const { prompt } = step;
    // 总超时默认 5 分钟（异步流程可能较久）
    const timeout = parseInt(step.timeout) || REFRESH_TOTAL_TIMEOUT_MS;
    // 刷新间隔默认 5 秒
    const refreshInterval = parseInt(step.refreshInterval) || REFRESH_INTERVAL_MS;
    const start = Date.now();
    let round = 0;

    logger.info(`刷新等待开始: "${prompt}" 总超时=${timeout}ms 刷新间隔=${refreshInterval}ms`);

    while (Date.now() - start < timeout) {
        round++;
        const elapsedSec = Math.round((Date.now() - start) / 1000);

        // 1. AI 检查当前页面条件是否满足
        try {
            await agent.aiAssert(prompt);
            logger.info(`刷新等待成功: 第${round}轮(${elapsedSec}s)条件满足`);
            return { success: true, action: 'aiWaitRefresh', prompt, round };
        } catch (checkErr) {
            logger.info(`第${round}轮(${elapsedSec}s)未满足，准备刷新: ${String(checkErr.message || checkErr).substring(0, 100)}`);
        }

        // 2. 刷新页面（domcontentloaded 避免 SPA 跳转导致 frame 分离）
        try {
            await page.reload({ waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });
        } catch (navErr) {
            logger.warn(`刷新导航被中止(可能页面跳转): ${navErr.message}`);
        }

        // 3. 等待页面渲染稳定
        await page.waitForTimeout(2000);

        // 4. 等待剩余刷新间隔（已扣除稳定等待时间）
        const remaining = refreshInterval - 2000;
        if (remaining > 0) {
            await page.waitForTimeout(remaining);
        }
    }

    return {
        success: false,
        error: `刷新等待超时(${timeout}ms)：条件 "${prompt}" 始终未满足`,
        action: 'aiWaitRefresh', prompt
    };
}

module.exports = { handleWaitRefresh };
