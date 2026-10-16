/**
 * 套件级登录态处理
 *
 * 流程：解析 F12 控制台导出的 localStorage JSON -> 首次导航建立 origin
 *      -> 注入 localStorage -> 刷新页面使登录态生效
 */

const { LOGIN_SETTLE_MS } = require('./config');
const logger = require('./logger');
const { safeGoto, safeReload } = require('./navigation');

/**
 * 解析 localStorage 登录态 JSON（F12 控制台导出的键值对象）
 * 兼容两种格式：
 *   1. 直接键值对象: {"token":"xxx","userInfo":"yyy"}
 *   2. 带 localStorage 包装: {"localStorage":{"token":"xxx"}}
 *
 * @param {string} loginStateJson 浏览器控制台导出的 JSON 字符串
 * @returns {object|null} 可直接写入 localStorage 的键值对象，或 null
 */
function parseLoginState(loginStateJson) {
    if (!loginStateJson || typeof loginStateJson !== 'string') {
        return null;
    }
    try {
        const parsed = JSON.parse(loginStateJson);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            return null;
        }
        // 兼容带 localStorage 包装的格式
        const data = parsed.localStorage && typeof parsed.localStorage === 'object'
            ? parsed.localStorage
            : parsed;
        const clean = {};
        for (const [key, value] of Object.entries(data)) {
            if (key && value !== undefined && value !== null) {
                clean[key] = String(value);
            }
        }
        return Object.keys(clean).length > 0 ? clean : null;
    } catch (e) {
        logger.warn('登录态JSON解析失败:', e.message);
        return null;
    }
}

/**
 * 将登录态注入页面并使其生效
 *
 * @param {import('playwright').Page} page
 * @param {string} fullUrl 目标地址（用于建立 origin）
 * @param {object} loginState parseLoginState 的返回值
 * @returns {Promise<boolean>} 是否执行了注入
 */
async function applyLoginState(page, fullUrl, loginState) {
    if (!loginState || Object.keys(loginState).length === 0) {
        return false;
    }

    // 1. 先导航一次建立页面 origin（domcontentloaded 更快，且容错 SPA 跳转导致的 ERR_ABORTED）
    await safeGoto(page, fullUrl, { label: '首次导航' });

    // 2. 注入 localStorage 登录态
    logger.info('注入localStorage登录态:', Object.keys(loginState).join(', '));
    await page.evaluate((data) => {
        for (const [key, value] of Object.entries(data)) {
            try { localStorage.setItem(key, value); } catch (e) { /* 忽略单键写入失败 */ }
        }
    }, loginState);

    // 3. 刷新使登录态生效，并留出时间等待登录后跳转/异步渲染完成
    await safeReload(page, { settleMs: LOGIN_SETTLE_MS, label: '刷新' });

    return true;
}

module.exports = { parseLoginState, applyLoginState };
