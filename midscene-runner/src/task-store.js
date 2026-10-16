/**
 * 运行中任务的内存登记表
 * 用途：/status 观测、避免同 executionId 重复登记
 */

const runningTasks = new Map();

/** 标记任务开始执行 */
function markRunning(executionId, startTime) {
    runningTasks.set(executionId, { status: 'RUNNING', startTime });
}

/** 标记任务结束（无论成功失败） */
function unmarkRunning(executionId) {
    runningTasks.delete(executionId);
}

/** 当前运行快照，供 /status 返回 */
function snapshot() {
    return {
        running: runningTasks.size,
        tasks: Array.from(runningTasks.keys())
    };
}

module.exports = { markRunning, unmarkRunning, snapshot };
