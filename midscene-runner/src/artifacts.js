/**
 * 产物落盘：截图与测试报告
 *
 * 设计要点：Runner 与 Java 后端同机部署，共享文件系统，
 * 因此直接把文件复制到后端静态资源目录（reports / screenshots），
 * 回调时只传 URL，避免通过 HTTP 回传几十 MB 的 base64
 * （否则回调请求体过大被拒，任务会卡在 RUNNING）。
 */

const fs = require('fs');
const path = require('path');
const {
    REPORT_DIR,
    BACKEND_REPORT_DIR,
    BACKEND_SCREENSHOT_DIR,
    REPORT_WAIT_MS,
} = require('./config');
const logger = require('./logger');

/**
 * 将本地生成的 Playwright 报告复制到后端 reports 目录
 * @param {string|number} executionId
 * @param {{name: string, path: string}} reportFile
 * @returns {string|null} 后端可访问的报告 URL（如 /reports/xxx.html）
 */
function copyReportToBackend(executionId, reportFile) {
    try {
        if (!fs.existsSync(BACKEND_REPORT_DIR)) {
            fs.mkdirSync(BACKEND_REPORT_DIR, { recursive: true });
        }
        const destPath = path.join(BACKEND_REPORT_DIR, `${executionId}.html`);
        fs.copyFileSync(reportFile.path, destPath);
        logger.info(`报告已复制到后端: ${destPath}`);
        return `/reports/${executionId}.html`;
    } catch (e) {
        logger.warn(`复制报告到后端失败: ${e.message}`);
        return null;
    }
}

/**
 * 将截图 buffer 保存到后端 screenshots 目录
 * @param {string|number} executionId
 * @param {Buffer} screenshotBuffer
 * @returns {string|null} 后端可访问的截图 URL（如 /screenshots/xxx.png）
 */
function saveScreenshotToBackend(executionId, screenshotBuffer) {
    try {
        if (!fs.existsSync(BACKEND_SCREENSHOT_DIR)) {
            fs.mkdirSync(BACKEND_SCREENSHOT_DIR, { recursive: true });
        }
        const destPath = path.join(BACKEND_SCREENSHOT_DIR, `${executionId}.png`);
        fs.writeFileSync(destPath, screenshotBuffer);
        logger.info(`截图已保存到后端: ${destPath}`);
        return `/screenshots/${executionId}.png`;
    } catch (e) {
        logger.warn(`保存截图到后端失败: ${e.message}`);
        return null;
    }
}

/**
 * 页面截图并落盘（失败不抛出，返回 null）
 * @param {import('playwright').Page} page
 * @param {string|number} executionId
 * @param {object} [options]
 * @param {boolean} [options.fullPage] 是否整页截图（成功场景用 true，失败兜底用 false）
 * @returns {Promise<string|null>} 截图 URL
 */
async function captureScreenshot(page, executionId, options = {}) {
    const { fullPage = true } = options;
    try {
        const screenshotBuffer = await page.screenshot({ fullPage });
        return saveScreenshotToBackend(executionId, screenshotBuffer);
    } catch (e) {
        logger.warn(`截图保存失败: ${e.message}`);
        return null;
    }
}

/**
 * 轮询等待 Playwright HTML 报告生成完毕
 * Midscene 异步写报告，需等待文件大小稳定（1 秒内大小不变且大于 0）才视为写完
 *
 * @param {number} [maxWaitMs] 最大等待毫秒数
 * @returns {Promise<{name: string, path: string, mtime: number}|null>} 报告文件信息或 null
 */
async function waitForReport(maxWaitMs = REPORT_WAIT_MS) {
    const start = Date.now();
    while (Date.now() - start < maxWaitMs) {
        try {
            if (fs.existsSync(REPORT_DIR)) {
                const files = fs.readdirSync(REPORT_DIR)
                    .filter(f => f.endsWith('.html'))
                    .map(f => ({
                        name: f,
                        path: path.join(REPORT_DIR, f),
                        mtime: fs.statSync(path.join(REPORT_DIR, f)).mtimeMs
                    }))
                    .sort((a, b) => b.mtime - a.mtime);

                if (files.length > 0) {
                    const latest = files[0];
                    const size1 = fs.statSync(latest.path).size;
                    await new Promise(r => setTimeout(r, 1000));
                    const size2 = fs.statSync(latest.path).size;
                    if (size2 === size1 && size2 > 0) {
                        logger.info(`等待到最新报告: ${latest.name}`);
                        return latest;
                    }
                }
            }
        } catch (e) { /* 文件可能正在写入，忽略 */ }
        await new Promise(r => setTimeout(r, 1000));
    }
    logger.warn(`等待报告超时(${maxWaitMs}ms)`);
    return null;
}

module.exports = {
    copyReportToBackend,
    saveScreenshotToBackend,
    captureScreenshot,
    waitForReport,
};
