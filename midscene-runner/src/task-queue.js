/**
 * 任务队列与并发闸门（Runner 侧的「排队」实现）
 *
 * 背景（为什么需要它）：
 *   原来 index.js 的 /execute 一收到任务就立刻 executeTest()，
 *   后端执行一个套件时是 for 循环把几十个用例「瞬间全部推过来」的，
 *   于是 Runner 会同时起几十个 Chromium、同时发起几十路 AI 调用，导致：
 *     1) 内存/CPU 打满，页面加载变慢，AI 步骤出现「假失败」；
 *     2) 云厂商（如阿里云百炼）的并发配额瞬间被打满，报 429 限流。
 *
 * 做法：
 *   所有任务先进 waiting 队列；只有当「正在执行的数量 < MAX_CONCURRENT_TASKS」
 *   时才从队首取一个出来执行，跑完一个立刻补下一个。
 *   并发上限来自 config.js 的 MAX_CONCURRENT_TASKS（默认 1，即完全串行）。
 *
 * 注意：这里只负责「什么时候开始跑」，「怎么跑」仍然全部在 executor.js 里，
 *      所以 executeTest 内部的浏览器开关、状态登记、结果回调逻辑都不受影响。
 */

const { MAX_CONCURRENT_TASKS } = require('./config');
const logger = require('./logger');
const { executeTest } = require('./executor');

/** 同时执行的任务上限（config.js 已保证是 >=1 的整数） */
const MAX = MAX_CONCURRENT_TASKS;

/** 等待开跑的任务（数组头部 = 下一个要跑的） */
const waiting = [];
/** 正在开跑的任务标识集合，size 就是当前并发数 */
const runningKeys = new Set();

/** 无 executionId 的兜底自增序号（正常后端一定会传 executionId） */
let fallbackSeq = 0;

/** 任务标识：优先用 executionId，异常情况下用序号兜底 */
function keyOf(task) {
    const executionId = task && task.executionId;
    if (executionId !== undefined && executionId !== null && String(executionId) !== '') {
        return String(executionId);
    }
    fallbackSeq += 1;
    return `未命名任务-${fallbackSeq}`;
}

/** 当前队列/并发快照，供 /status 观测 */
function stats() {
    return {
        running: runningKeys.size,
        queued: waiting.length,
        maxConcurrent: MAX,
    };
}

/**
 * 有空位就继续取任务执行，直到「没有空位」或「队列已空」。
 * 每跑完一个任务会再调用一次本函数，所以是「跑完一个立刻补一个」。
 */
function drain() {
    while (runningKeys.size < MAX && waiting.length > 0) {
        const task = waiting.shift();
        const key = keyOf(task);
        // 从入队到「真正开跑」的排队耗时，用于日志和「开跑通知」，
        // 便于区分「任务被队列拖慢了」和「任务本身执行慢」
        const queueWaitMs = task && task.__enqueuedAt ? Date.now() - task.__enqueuedAt : 0;

        runningKeys.add(key);
        logger.info(`开始执行 executionId=${key}（并发 ${runningKeys.size}/${MAX}，`
            + `排队等待 ${queueWaitMs}ms，队列剩余 ${waiting.length}）`);

        // 用 Promise.resolve().then 包一层：即使 executeTest 同步抛错，
        // 也一定能走到 finally 把并发名额释放掉，不会把队列卡死。
        Promise.resolve()
            .then(() => executeTest(task, { queueWaitMs }))
            .catch(err => logger.error(`任务执行异常 executionId=${key}`, err))
            .finally(() => {
                runningKeys.delete(key);
                logger.info(`任务结束 executionId=${key}（队列剩余 ${waiting.length}）`);
                // 释放出的名额立刻给下一个任务
                drain();
            });
    }
}

/**
 * 任务入队（index.js 的 /execute 调用）
 *
 * @param {object} task 后端下发的任务体
 * @returns {{executionId: string, position: number, running: number, queued: number}}
 *          position = 0 表示有空位、马上就会开跑；>0 表示排在它前面的任务个数
 */
function enqueue(task) {
    const key = keyOf(task);
    // 记录入队时刻，用来算「排队等待了多久」
    if (task && typeof task === 'object') {
        task.__enqueuedAt = Date.now();
    }

    const willStartNow = runningKeys.size < MAX;
    waiting.push(task);

    // indexOf 用的是同一个对象引用，所以能准确算出前面还有几个
    const position = willStartNow ? 0 : waiting.indexOf(task) + 1;
    logger.info(`任务入队 executionId=${key}，排在队列第 ${position} 位`
        + `（当前并发 ${runningKeys.size}/${MAX}，队列长度 ${waiting.length}）`);

    drain();

    return { executionId: key, position, ...stats() };
}

module.exports = { enqueue, stats };
