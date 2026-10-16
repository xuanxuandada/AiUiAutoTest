/**
 * Midscene.js 测试执行器（入口）
 *
 * 功能：
 * 1. 接收后端 Spring Boot 发送的测试任务
 * 2. 使用 Midscene.js + Playwright 执行 UI 自动化测试
 * 3. 将执行结果回调给后端
 *
 * 本文件只负责 HTTP 服务与路由，具体执行逻辑见 src/ 目录：
 *   src/config.js        端口、路径、超时等全局配置
 *   src/logger.js        统一日志（[Runner] 前缀）
 *   src/executor.js      任务执行编排主流程
 *   src/task-queue.js    任务队列与并发闸门（所有任务先排队再开跑）
 *   src/browser.js       浏览器启动与页面创建
 *   src/navigation.js    容错导航/刷新
 *   src/login-state.js   套件级 localStorage 登录态注入
 *   src/steps/           步骤执行器（含上传、刷新等待）
 *   src/artifacts.js     截图与报告落盘
 *   src/callback.js      执行结果回调后端
 *   src/task-store.js    运行中任务登记表
 *
 * 启动方式：
 *   cd midscene-runner
 *   npm install
 *   npx playwright install chromium
 *   npm start
 */

// config 内部会先加载 .env，必须最先引入
const { PORT, JSON_BODY_LIMIT, MAX_CONCURRENT_TASKS } = require('./src/config');
const logger = require('./src/logger');
const { enqueue, stats } = require('./src/task-queue');
const { snapshot } = require('./src/task-store');
const express = require('express');

const app = express();
// 报告 HTML base64 较大（实测 45MB+），需放宽 JSON 请求体上限，
// 否则回调被拒导致任务卡在 RUNNING
app.use(express.json({ limit: JSON_BODY_LIMIT }));

/**
 * POST /execute - 接收测试任务（入队后立即返回，由队列按并发上限逐个执行）
 */
app.post('/execute', (req, res) => {
    const task = req.body || {};
    const { executionId, testCaseId, targetUrl, loginFetchCommand } = task;

    logger.info(`收到任务 executionId=${executionId}, testCaseId=${testCaseId}, url=${targetUrl}, 登录态=${loginFetchCommand ? '有' : '无'}`);

    // 只入队，不立刻开跑：后端一次性推来几十个用例时，
    // 由 task-queue.js 按 MAX_CONCURRENT_TASKS 决定什么时候真正执行，
    // 避免瞬间起几十个 Chromium、同时把云厂商配额打满。
    const info = enqueue(task);

    // 立即返回 200。
    // 注意：返回 200 只代表「Runner 收下了任务」，不代表「已经开始执行」；
    // 被排队的任务要等到真正开跑时，Runner 才会回调后端把状态改成 RUNNING。
    res.json({
        success: true,
        executionId,
        message: info.position === 0
            ? '任务已接受，即将开始执行'
            : `任务已接受，排在队列第 ${info.position} 位`,
    });
});

/**
 * GET /status - 查看当前运行状态（含队列长度与并发上限）
 */
app.get('/status', (req, res) => {
    const store = snapshot();
    const queue = stats();
    res.json({
        // 下面三个字段保持原有含义/名字不变，加了队列后语义更清楚
        running: store.running,        // 正在执行的任务数
        tasks: store.tasks,            // 正在执行的任务 executionId 列表
        // 新增：排队观测
        queued: queue.queued,          // 已接收、还在排队等待开跑的任务数
        maxConcurrent: queue.maxConcurrent,
    });
});

// 启动服务
app.listen(PORT, () => {
    logger.info(`Midscene.js 测试执行器已启动，端口: ${PORT}`);
    logger.info(`任务并发上限: ${MAX_CONCURRENT_TASKS}（1 = 完全串行，可在 .env 用 MAX_CONCURRENT_TASKS 调整）`);
    logger.info('等待后端发送测试任务...');
});
