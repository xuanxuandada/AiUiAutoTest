/**
 * 统一日志：保持原有 [Runner] 前缀，便于日志检索与问题定位
 */

const PREFIX = '[Runner]';

module.exports = {
    info: (...args) => console.log(PREFIX, ...args),
    warn: (...args) => console.warn(PREFIX, ...args),
    error: (...args) => console.error(PREFIX, ...args),
};
