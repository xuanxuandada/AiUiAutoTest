/**
 * 测试任务执行编排主流程
 *
 * 流程：
 *   登记运行态 + 开跑通知后端 -> 启动浏览器 -> 建立页面 -> 注入登录态（可选）
 *   -> 创建 Midscene Agent -> 逐步执行 + 自动断言
 *   -> 截图/报告落盘 -> 回调后端
 * 失败分支：收集已执行的步骤详情，尽力截图与报告，再回调后端。
 */

const { PlaywrightAgent } = require('@midscene/web');

const { NAV_SETTLE_MS, REPORT_WAIT_MS } = require('./config');
const logger = require('./logger');
const { launchBrowser, createPage, getLastPage } = require('./browser');
const { normalizeUrl, safeGoto } = require('./navigation');
const { parseLoginState, applyLoginState } = require('./login-state');
const { executeStep } = require('./steps');
const { copyReportToBackend, captureScreenshot, waitForReport } = require('./artifacts');
const { callbackToBackend, notifyStartToBackend } = require('./callback');
const { markRunning, unmarkRunning } = require('./task-store');

/**
 * 逐步执行测试步骤，任一步失败或断言失败即抛出中断
 * @param {object} agent Midscene PlaywrightAgent
 * @param {import('playwright').Page} page
 * @param {Array} steps 步骤列表
 * @param {Array} stepResults 结果收集数组（由调用方传入，失败时也要回传）
 */
async function runSteps(agent, page, steps, stepResults) {
    for (let i = 0; i < steps.length; i++) {
        const step = steps[i];
        logger.info(`执行步骤 ${i + 1}/${steps.length}: ${JSON.stringify(step)}`);

        const stepResult = await executeStep(agent, page, step);

        // 步骤执行成功且有预期结果，自动断言
        //
        // 这里【绝不能在 catch 里直接 throw】：那样会跳过下面的 stepResults.push，
        // 导致「断言失败的那一步」自己的结果（含 assertError）压根没进 stepResults。
        // 后果是后端收到的步骤列表里刚好缺了最关键的那一条，
        // 报告和 AI 诊断都少了现场，只能看到一个「步骤 N 断言失败」的笼统结论。
        // 所以先把失败原因存起来，等这一步的结果 push 完，再统一抛出去中断后续步骤。
        let assertFailure = null;
        if (stepResult.success && step.expectedResult) {
            logger.info(`步骤 ${i + 1} 预期结果断言: ${step.expectedResult}`);
            try {
                await agent.aiAssert(step.expectedResult);
                stepResult.assertResult = 'PASSED';
                logger.info(`步骤 ${i + 1} 断言通过`);
            } catch (assertErr) {
                stepResult.assertResult = 'FAILED';
                stepResult.assertError = assertErr.message;
                assertFailure = `步骤 ${i + 1} 断言失败: ${step.expectedResult}，原因: ${assertErr.message}`;
            }
        }

        stepResults.push({
            stepIndex: i,
            step,
            ...stepResult
        });

        // 断言失败优先抛出（此时该步骤的结果已经进 stepResults 了）
        if (assertFailure) {
            throw new Error(assertFailure);
        }

        if (!stepResult.success) {
            throw new Error(`步骤 ${i + 1} 执行失败: ${stepResult.error}`);
        }
    }
}

/**
 * 报告落盘并回填 URL（成功/失败分支共用）
 */
async function attachReport(result, executionId, logPrefix) {
    const reportFile = await waitForReport(REPORT_WAIT_MS);
    if (reportFile) {
        const reportUrl = copyReportToBackend(executionId, reportFile);
        if (reportUrl) result.reportUrl = reportUrl;
        logger.info(`${logPrefix}报告已复制到后端: ${reportFile.name}`);
    }
}

/**
 * 根据后端下发的「当前登录用户自己的」AI配置构造 Midscene 的 modelConfig。
 *
 * 为什么要这样做：
 *   Midscene 默认从进程环境变量（.env / process.env）读模型配置，这样一来所有用户
 *   都会用同一把 Key。一旦传入 modelConfig，Midscene 内部会进入 isolatedMode，
 *   完全绕过 process.env、只认这里传进来的值，因此多个用户并发执行也不会串用 Key。
 *
 * 注意：
 *   1) key 名必须是「环境变量名本身」（MIDSCENE_MODEL_XXX），不是友好名。
 *   2) MIDSCENE_MODEL_BASE_URL 要的是「基址」（.../compatible-mode/v1），
 *      不带 /chat/completions，后端已帮我们归一化。
 *   3) 值为 undefined 的项会被 Midscene 自动跳过，所以缺字段不会报错。
 *
 * @param {object} cfg 后端任务体里携带的 AI 配置字段
 * @returns {object|undefined} 拿到 Key 才返回配置；没有 Key 返回 undefined（调用方会拒绝执行）
 */
function buildUserModelConfig({ aiApiKey, aiModel, aiModelFamily, aiBaseUrl }) {
    if (!aiApiKey) return undefined;
    return {
        MIDSCENE_MODEL_API_KEY: aiApiKey,
        MIDSCENE_MODEL_NAME: aiModel || undefined,
        MIDSCENE_MODEL_FAMILY: aiModelFamily || undefined,
        MIDSCENE_MODEL_BASE_URL: aiBaseUrl || undefined
    };
}

/**
 * 执行单个测试任务（异常内部消化，不向外抛出）
 *
 * 本函数由 task-queue.js 在「抢到并发名额」后才调用，
 * 也就是说：被调用时就是任务真正开跑的时刻（之前可能已在队列里排了很久）。
 *
 * @param {object} task 后端下发的任务体
 * @param {object} [options] 调度信息
 * @param {number} [options.queueWaitMs] 在队列里等待了多久，仅用于日志与开跑通知
 */
async function executeTest(task, options = {}) {
    const { queueWaitMs = 0 } = options;
    const { executionId, testCaseId, targetUrl, steps, expectedResult, callbackUrl,
        loginFetchCommand, aiApiKey, aiModel, aiModelFamily, aiBaseUrl } = task;
    let browser = null;
    const startTime = Date.now();
    // 步骤结果集（try/catch 共用，失败时也要回传详情）
    const stepResults = [];

    // 当前登录用户自己的 AI 配置（后端下发）。
    // 系统规定「必须用用户自己的 Key」，所以这里拿不到 Key 就直接拒绝执行，
    // 绝不回退到 .env 里的全局 Key。
    const modelConfig = buildUserModelConfig({ aiApiKey, aiModel, aiModelFamily, aiBaseUrl });

    try {
        // 「必须配自己的 Key」的第二道防线：
        // 后端在触发执行前已经用 requireForCurrentUser() 校验过一次，这里再兜一道，
        // 防止任何绕过 Java 后端、直连 Runner 的调用偷偷用上全局 Key。
        // 抛出后会被下面的 catch 捕获，回传成一条 FAILED 执行记录（不会直接断掉）。
        if (!modelConfig) {
            throw new Error('未收到当前登录用户的AI配置（apiKey 为空），已拒绝执行。'
                + '请先到【设置 → AI模型配置】保存你自己的 API Key 后再试');
        }

        markRunning(executionId, startTime);

        // 通知后端「这条任务真正开跑了」，把执行记录从 PENDING 推进到 RUNNING。
        // 放在这里而不是收到请求时：加了队列后，任务可能排很久才轮到，
        // 后端收到 /execute 的 200 只代表「收下了」，不代表「开始跑了」。
        // 通知失败不阻塞执行（内部已消化异常），后端最终仍会收到结果回调。
        if (callbackUrl) {
            await notifyStartToBackend(callbackUrl, executionId, queueWaitMs);
        }

        // 启动浏览器与页面
        browser = await launchBrowser();
        const page = await createPage(browser);

        // 套件级登录态注入：解析 localStorage 登录态 JSON
        const loginState = loginFetchCommand ? parseLoginState(loginFetchCommand) : null;

        // 导航到目标 URL（自动补全协议）
        const fullUrl = normalizeUrl(targetUrl);
        logger.info(`导航到: ${fullUrl}`);

        if (loginState && Object.keys(loginState).length > 0) {
            await applyLoginState(page, fullUrl, loginState);
        } else {
            await safeGoto(page, fullUrl, { settleMs: NAV_SETTLE_MS, label: '导航' });
        }

        // 创建 Midscene Agent
        // 强制携带「当前登录用户自己的」modelConfig，Midscene 会进入 isolatedMode，
        // 完全绕过 process.env，多个用户并发执行也不会串用 Key。
        logger.info(`使用当前登录用户自己的AI配置创建Agent: model=${aiModel}, modelFamily=${aiModelFamily}, `
            + `baseUrl=${aiBaseUrl}, apiKey=***${String(aiApiKey).slice(-4)}`);
        const agent = new PlaywrightAgent(page, { modelConfig });

        // 逐步执行测试步骤
        await runSteps(agent, page, steps, stepResults);

        // 执行成功
        const duration = Date.now() - startTime;
        const result = {
            executionId,
            testCaseId,
            status: 'PASSED',
            durationMs: duration,
            stepResults,
            expectedResult,
            timestamp: new Date().toISOString()
        };

        logger.info(`测试通过 executionId=${executionId}, 耗时=${duration}ms`);

        // 截图：直接保存到后端 screenshots 目录，回调只传 URL（避免传 base64）
        const shotUrl = await captureScreenshot(page, executionId, { fullPage: true });
        if (shotUrl) result.screenshotUrl = shotUrl;

        // 报告：复制到后端 reports 目录，回调只传 URL（避免传几十 MB base64）
        await attachReport(result, executionId, '测试');

        // 回调后端
        await callbackToBackend(callbackUrl, result);

    } catch (error) {
        const duration = Date.now() - startTime;
        const result = {
            executionId,
            testCaseId,
            status: 'FAILED',
            durationMs: duration,
            errorMessage: error.message,
            stepResults, // 回传步骤详情（含每步断言结果），避免断言失败信息丢失
            timestamp: new Date().toISOString()
        };

        logger.error(`测试失败 executionId=${executionId}: ${error.message}`);

        // 失败时也尝试截图（直接保存到后端 screenshots 目录）
        if (browser) {
            const lastPage = getLastPage(browser);
            if (lastPage) {
                const shotUrl = await captureScreenshot(lastPage, executionId, { fullPage: false });
                if (shotUrl) result.screenshotUrl = shotUrl;
            }
        }

        // 失败时也复制报告到后端 reports 目录，保证失败有报告可看
        await attachReport(result, executionId, '失败时');

        await callbackToBackend(callbackUrl, result);

    } finally {
        unmarkRunning(executionId);
        if (browser) {
            await browser.close();
        }
    }
}

module.exports = { executeTest };
