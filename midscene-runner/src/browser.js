/**
 * 浏览器生命周期管理：启动 chromium、创建页面、取最近活跃页面
 */

const { chromium } = require('playwright');

/**
 * 启动无头 chromium（服务器环境）
 */
async function launchBrowser() {
    return chromium.launch({
        headless: true,
        args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
}

/**
 * 基于浏览器创建上下文与页面（统一视口与中文环境）
 */
async function createPage(browser) {
    const context = await browser.newContext({
        viewport: { width: 1920, height: 1080 },
        locale: 'zh-CN'
    });
    return await context.newPage();
}

/**
 * 获取最近打开的页面（失败兜底截图时使用，页面可能已跳转/新开）
 */
function getLastPage(browser) {
    try {
        const pages = browser.contexts()?.[0]?.pages();
        if (pages && pages.length > 0) {
            return pages[pages.length - 1];
        }
    } catch (e) { /* ignore */ }
    return null;
}

module.exports = { launchBrowser, createPage, getLastPage };
