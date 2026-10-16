/**
 * 上传文件路径解析
 *
 * 支持从自然语言描述中提取本地 Windows 绝对路径，
 * 并按优先级回退到步骤字段与 uploads 目录。
 */

const fs = require('fs');
const path = require('path');
const { UPLOAD_DIR } = require('../config');

/**
 * 从中文描述中提取本地文件路径
 * 支持三种写法：
 *   1. 带引号: "C:\Users\adm\Desktop\xxx.jpg"
 *   2. 无引号且带扩展名: C:\Users\adm\Desktop\xxx.jpg（遇到空白或中文标点停止）
 *   3. 无扩展名的裸路径
 *
 * @param {string} prompt 自然语言描述，如 "点击上传按钮，选择文件C:\Users\adm\Desktop\xxx.jpg"
 * @returns {string|null} 文件路径或 null
 */
function extractFilePath(prompt) {
    if (!prompt || typeof prompt !== 'string') return null;

    // 1. 优先匹配带引号的路径（路径内含空格时更可靠）
    const quoted = prompt.match(/["']([A-Za-z]:[\\/][^"']+)["']/);
    if (quoted) return quoted[1].trim();

    // 2. 匹配无引号的 Windows 绝对路径：以文件扩展名结尾，避免把路径后的中文文字也吞进来
    const withExt = prompt.match(/[A-Za-z]:[\\/][^\s，。；、,，！!？?（）()<>"']*\.[A-Za-z0-9]+/);
    if (withExt) return withExt[0].trim();

    // 3. 无扩展名的路径（遇到空白或中文标点停止）
    const bare = prompt.match(/[A-Za-z]:[\\/][^\s，。；、,，！!？?（）()<>"']+/);
    if (bare) return bare[0].trim();

    return null;
}

/**
 * 解析 upload 步骤的实际文件路径
 * 优先级：中文描述中解析的路径 > step.filePath > step.value > uploads 目录下的唯一测试文件
 *
 * @param {object} step 步骤对象
 * @returns {string|null} 文件路径或 null
 */
function resolveUploadFilePath(step) {
    const fromPrompt = extractFilePath(step.prompt);
    if (fromPrompt) return fromPrompt;

    if (step.filePath && typeof step.filePath === 'string' && step.filePath.trim()) {
        return step.filePath.trim();
    }
    if (step.value && typeof step.value === 'string' && step.value.trim()) {
        return step.value.trim();
    }

    // 兜底：uploads 目录下唯一的测试文件
    try {
        if (fs.existsSync(UPLOAD_DIR)) {
            const files = fs.readdirSync(UPLOAD_DIR).filter(f => {
                try { return fs.statSync(path.join(UPLOAD_DIR, f)).isFile(); } catch (e) { return false; }
            });
            if (files.length === 1) {
                return path.join(UPLOAD_DIR, files[0]);
            }
        }
    } catch (e) { /* ignore */ }

    return null;
}

module.exports = { extractFilePath, resolveUploadFilePath };
