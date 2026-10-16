/**
 * 页面导航工具
 *
 * 统一约定：所有导航使用 domcontentloaded 而非 networkidle。
 * 原因：SPA 加载后仍会持续发起请求或自动跳转，networkidle 极易被新导航打断或超时；
 *      且跳转/重定向会抛出 ERR_ABORTED，属于预期内情况，需容错而非中断流程。
 */

const { NAV_TIMEOUT_MS } = require('./config');
const logger = require('./logger');

/**
 * 补全协议前缀（用户配置可能只填域名）
 */
function normalizeUrl(url) {
    if (!url) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return 'https://' + url;
}

/**
 * 容错导航
 * @param {import('playwright').Page} page
 * @param {string} url 目标地址
 * @param {object} [options]
 * @param {number} [options.settleMs] 导航后额外等待的稳定时间
 * @param {string} [options.label] 日志标签，如"首次导航"
 */
async function safeGoto(page, url, options = {}) {
    const { settleMs = 0, label = '导航' } = options;
    try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });
    } catch (navErr) {
        logger.warn(`${label}被中止（可能页面正在跳转），继续执行: ${navErr.message}`);
    }
    if (settleMs > 0) {
        await page.waitForTimeout(settleMs);
    }
}

/**
 * 容错刷新（domcontentloaded 避免 SPA 跳转导致 frame 分离）
 */
async function safeReload(page, options = {}) {
    const { settleMs = 0, label = '刷新' } = options;
    try {
        await page.reload({ waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });
    } catch (navErr) {
        logger.warn(`${label}导航被中止（可能页面正在跳转），继续执行: ${navErr.message}`);
    }
    if (settleMs > 0) {
        await page.waitForTimeout(settleMs);
    }
}

module.exports = { normalizeUrl, safeGoto, safeReload };
