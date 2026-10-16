/**
 * 执行结果回调：通知 Java 后端更新执行记录
 *
 * 两类通知共用同一个回调地址：
 *   1) 开跑通知（phase=start）：任务「真正开始执行」时发一次，让后端把记录推进到 RUNNING；
 *   2) 结果通知（不带 phase）：任务结束时发一次，携带 PASSED/FAILED 等终态与详情。
 *
 * 注意：当前实现失败仅记录日志、不重试。
 * 若结果回调失败，后端记录会停留在 RUNNING 状态，
 * 需要依赖后端侧的"卡死任务超时巡检"兜底。
 */

const logger = require('./logger');

/**
 * @param {string} callbackUrl 后端回调地址
 * @param {object} result 执行结果（含 status / stepResults / screenshotUrl / reportUrl 等）
 * @returns {Promise<boolean>} 是否回调成功
 */
async function callbackToBackend(callbackUrl, result) {
    if (!callbackUrl) {
        logger.info('无回调URL，跳过回调');
        return false;
    }

    try {
        const response = await fetch(callbackUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(result)
        });

        if (response.ok) {
            logger.info(`回调成功 executionId=${result.executionId}`);
            return true;
        }
        logger.error(`回调失败 status=${response.status}`);
        return false;
    } catch (error) {
        logger.error(`回调异常: ${error.message}`);
        return false;
    }
}

/**
 * 开跑通知：告诉后端「这条任务真正开始执行了」，把执行记录从 PENDING 推进到 RUNNING。
 *
 * 为什么需要它：
 *   加了任务队列后，一个套件里的用例会排队逐个执行，后面的可能等很久才开跑。
 *   后端收到 /execute 的 200 只代表「Runner 收下了」，并不代表「开始跑了」，
 *   所以后端不再在收到 200 时置 RUNNING，而是等这条通知。
 *   这样界面上就不会出现「一排 RUNNING 但实际没动」。
 *
 * 失败只记日志、不重试：即使这条通知丢了，也只是界面上「多显示一段时间排队中」，
 * 结果回调到达时依然会落成 PASSED/FAILED，不会造成任务卡死。
 *
 * @param {string} callbackUrl 后端回调地址
 * @param {string|number} executionId 执行记录ID
 * @param {number} queueWaitMs 排队等待时长（便于判断任务是被队列拖慢还是本身执行慢）
 * @returns {Promise<boolean>} 是否通知成功
 */
async function notifyStartToBackend(callbackUrl, executionId, queueWaitMs) {
    if (!callbackUrl) {
        logger.info('无回调URL，跳过开跑通知');
        return false;
    }

    try {
        const response = await fetch(callbackUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            // phase=start 是约定好的「开跑通知」标记：
            // 后端只据它把状态改成 RUNNING，绝不会写结束时间/耗时/结果详情
            body: JSON.stringify({
                executionId,
                phase: 'start',
                status: 'RUNNING',
                queueWaitMs: queueWaitMs || 0,
            })
        });

        if (response.ok) {
            logger.info(`开跑通知成功 executionId=${executionId}, 排队等待=${queueWaitMs || 0}ms`);
            return true;
        }
        logger.warn(`开跑通知失败 status=${response.status} executionId=${executionId}`);
        return false;
    } catch (error) {
        logger.warn(`开跑通知异常 executionId=${executionId}: ${error.message}`);
        return false;
    }
}

module.exports = { callbackToBackend, notifyStartToBackend };
