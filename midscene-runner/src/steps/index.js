/**
 * 步骤执行器：按 action 分发到具体操作
 *
 * 支持的 action：
 *   back          浏览器后退
 *   navigate      页面跳转（跨页面测试）
 *   aiAction      AI 自然语言操作（prompt 含文件路径时自动转 upload）
 *   aiInput       AI 输入文本
 *   aiAssert      AI 断言
 *   aiWaitFor     AI 等待条件（不刷新页面）
 *   aiWaitRefresh 周期性刷新并等待条件
 *   aiTap         AI 点击（prompt 含文件路径时自动转 upload）
 *   upload        上传附件
 *   aiScroll      滚动
 *   sleep         固定等待
 *
 * 约定：步骤内部异常一律转换为 { success: false, error } 返回，
 *      由上层 executeTest 决定是否中断整条用例。
 */

const { NAV_TIMEOUT_MS, NAVIGATE_SETTLE_MS } = require('../config');
const logger = require('../logger');
const { normalizeUrl, safeGoto } = require('../navigation');
const { extractFilePath } = require('./file-path');
const { handleUpload } = require('./upload');
const { handleWaitRefresh } = require('./wait-refresh');

/**
 * @param {object} agent Midscene PlaywrightAgent
 * @param {import('playwright').Page} page
 * @param {object} step 步骤对象
 * @returns {Promise<object>} {success, action, ...}
 */
async function executeStep(agent, page, step) {
    const { action, prompt, value, url } = step;

    try {
        switch (action) {
            case 'back': {
                // 浏览器后退（返回上一页），页面同页签内跳转，历史记录仍然有效
                logger.info('浏览器后退，返回上一页');
                await page.goBack({ waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });
                return { success: true, action: 'back' };
            }

            case 'navigate': {
                // 页面跳转（跨页面测试支持）
                if (!url) {
                    return { success: false, error: 'navigate 操作缺少 url 字段', action };
                }
                const navUrl = normalizeUrl(url);
                logger.info(`导航到新页面: ${navUrl}`);
                await safeGoto(page, navUrl, { settleMs: NAVIGATE_SETTLE_MS, label: '导航' });
                return { success: true, action: 'navigate', url: navUrl };
            }

            case 'aiAction': {
                // Midscene 核心：用自然语言执行操作
                // 若描述中包含本地文件路径（如"点击上传按钮，选择文件C:\xxx.jpg"），自动走上传逻辑
                if (extractFilePath(prompt)) {
                    return await handleUpload(agent, page, { ...step, action: 'upload' });
                }
                await agent.aiAction(prompt);
                return { success: true, action: 'aiAction', prompt };
            }

            case 'aiInput': {
                // 输入文本
                await agent.aiAction(`在"${prompt}"中输入"${value}"`);
                return { success: true, action: 'aiInput', prompt, value };
            }

            case 'aiAssert': {
                // AI 断言
                await agent.aiAssert(prompt);
                return { success: true, action: 'aiAssert', prompt };
            }

            case 'aiWaitFor': {
                // 等待条件
                await agent.aiWaitFor(prompt);
                return { success: true, action: 'aiWaitFor', prompt };
            }

            case 'aiWaitRefresh': {
                // 周期性刷新页面并等待条件满足（适合异步流程：提交后状态慢慢变化，需刷新才更新）
                return await handleWaitRefresh(agent, page, step);
            }

            case 'aiTap': {
                // 点击；若描述中包含本地文件路径，自动走上传逻辑
                if (extractFilePath(prompt)) {
                    return await handleUpload(agent, page, { ...step, action: 'upload' });
                }
                await agent.aiAction(`点击${prompt}`);
                return { success: true, action: 'aiTap', prompt };
            }

            case 'upload': {
                // 上传附件：AI 点击上传按钮 -> 捕获文件选择框 -> 注入本地文件
                // 文件路径来源：中文描述中解析 > step.filePath > step.value > uploads目录唯一文件
                return await handleUpload(agent, page, step);
            }

            case 'aiScroll': {
                // 滚动
                await agent.aiAction(prompt || '向下滚动页面');
                return { success: true, action: 'aiScroll', prompt };
            }

            case 'sleep': {
                // 等待指定时间
                await page.waitForTimeout(parseInt(value) || 1000);
                return { success: true, action: 'sleep', value };
            }

            default:
                return { success: false, error: `未知操作类型: ${action}` };
        }
    } catch (error) {
        return { success: false, error: error.message, action, prompt };
    }
}

module.exports = { executeStep };
