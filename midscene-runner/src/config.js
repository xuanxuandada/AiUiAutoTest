/**
 * 全局配置：路径、端口、超时等常量集中管理
 *
 * 所有路径统一以 midscene-runner 目录为基准解析，
 * 避免各子模块因自身 __dirname 不同导致路径错乱。
 */

const path = require('path');

// 必须在读取 process.env 前加载 .env
require('dotenv').config();

// midscene-runner 目录
const ROOT_DIR = path.resolve(__dirname, '..');
// 项目根目录（Java 后端所在目录，与 midscene-runner 同级）
const PROJECT_DIR = path.resolve(ROOT_DIR, '..');

// 上传文件兜底目录
const UPLOAD_DIR = path.join(ROOT_DIR, 'uploads');
// Midscene 本地报告目录（Playwright HTML 报告落盘位置）
const REPORT_DIR = path.join(ROOT_DIR, 'midscene_run', 'report');
// 后端可访问的报告/截图目录（同机文件系统共享，避免走 HTTP 传 base64）
//
// 报告与截图已迁到独立磁盘 G:\aitest，并拆成两个子目录分开存放
// （原为项目根目录下的 reports/、screenshots/）：
//   - 报告(HTML) → G:\aitest\reports\{executionId}.html
//   - 截图(PNG)  → G:\aitest\screenshots\{executionId}.png
// ⚠️ 必须与后端 application.yml 的 app.artifact.report-dir / screenshot-dir 保持一致，
//    否则 Runner 写入端和后端读取端会对不上（前端打开报告/截图会 404）。
// 可用环境变量 BACKEND_REPORT_DIR / BACKEND_SCREENSHOT_DIR 覆盖（如换盘/换机器时）。
const BACKEND_REPORT_DIR = process.env.BACKEND_REPORT_DIR || 'G:/aitest/reports';
const BACKEND_SCREENSHOT_DIR = process.env.BACKEND_SCREENSHOT_DIR || 'G:/aitest/screenshots';

module.exports = {
    PORT: process.env.PORT || 3001,

    // 路径
    ROOT_DIR,
    PROJECT_DIR,
    UPLOAD_DIR,
    REPORT_DIR,
    BACKEND_REPORT_DIR,
    BACKEND_SCREENSHOT_DIR,

    // 报告 base64 较大（实测 45MB+），需放宽 JSON 请求体上限，
    // 否则回调被拒导致任务卡在 RUNNING
    JSON_BODY_LIMIT: '100mb',

    // ============ 任务并发控制（task-queue.js 使用） ============
    // 同时执行的测试任务上限：每个任务都会开一个 Chromium 并持续调 AI。
    // 默认 1 = 完全串行（最稳，能避免内存/CPU 打满、页面变慢导致的「假失败」，
    // 也能避免云厂商并发配额被瞬间打满报 429）。
    // 机器配置好、且云厂商配额充足时可调大到 2~3；非法值（空/0/负数/非数字）一律回退为 1。
    MAX_CONCURRENT_TASKS: (() => {
        const n = parseInt(process.env.MAX_CONCURRENT_TASKS, 10);
        return Number.isInteger(n) && n > 0 ? n : 1;
    })(),

    // 页面导航超时（统一用 domcontentloaded，SPA 更容错）
    NAV_TIMEOUT_MS: 30000,
    // 无登录态导航后等待页面稳定的时间
    NAV_SETTLE_MS: 1000,
    // navigate 步骤跳转后等待 SPA 渲染稳定的时间（原实现为 2000，勿与 NAV_SETTLE_MS 混用）
    NAVIGATE_SETTLE_MS: 2000,
    // 注入登录态后等待页面稳定（等登录后跳转/异步渲染完成）
    LOGIN_SETTLE_MS: 2000,

    // 轮询等待 Playwright 报告生成的默认超时
    REPORT_WAIT_MS: 30000,

    // aiWaitRefresh：总超时与刷新间隔
    REFRESH_TOTAL_TIMEOUT_MS: 300000,
    REFRESH_INTERVAL_MS: 5000,

    // upload：等待文件选择框超时、注入后等待页面处理时间
    FILE_CHOOSER_TIMEOUT_MS: 15000,
    UPLOAD_SETTLE_MS: 2000,
};
