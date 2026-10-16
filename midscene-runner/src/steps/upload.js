/**
 * 上传附件步骤
 *
 * 原理：AI 通过自然语言点击上传按钮 -> 页面触发 input[type=file] ->
 *      Playwright 捕获 filechooser 事件后 setFiles 注入本地文件（不弹 Windows 对话框）
 *
 * 多级兜底（任一成功即返回）：
 *   方式1：filechooser 事件捕获（最接近真实用户操作）
 *   方式0：页面已存在 input[type=file] 的快速路径
 *   方式2：直接定位第一个 input[type=file]
 *   方式3：遍历全部 input[type=file] 逐个尝试（含隐藏的 file input）
 */

const fs = require('fs');
const { FILE_CHOOSER_TIMEOUT_MS, UPLOAD_SETTLE_MS } = require('../config');
const logger = require('../logger');
const { extractFilePath, resolveUploadFilePath } = require('./file-path');

/**
 * 清洗 AI 点击指令：剔除文件路径与噪音，提取明确的"点击XX按钮"目标
 * 目的：避免 AI 把文件路径误当成页面元素去点击，导致 replan 超限反复重试
 */
function buildClickPrompt(prompt, pathInPrompt) {
    let cleanPrompt = (pathInPrompt ? prompt.replace(pathInPrompt, '') : prompt)
        .replace(/["'“”‘’]+/g, '')          // 去残留引号
        .replace(/^\s*\d+[.、．）)]\s*/g, '')   // 去步骤编号前缀，如 "4." "1、"
        .trim();

    // 提取核心动作短语（保留完整短语而非单字，如"上传按钮"而不是"上传"，避免AI定位模糊反复重试）
    const coreMatch = cleanPrompt.match(/上传(?:按钮|文件|附件)?|选择文件(?:按钮)?|添加(?:文件|附件)|导入文件|上传附件/);
    if (coreMatch) {
        cleanPrompt = coreMatch[0];
    }

    // 确保指令是明确的"点击XX按钮"形式，给 AI 清晰的点击目标
    if (cleanPrompt) {
        cleanPrompt = /按钮|控件|入口|区域/.test(cleanPrompt)
            ? '点击' + cleanPrompt
            : '点击' + cleanPrompt + '按钮';
    } else {
        cleanPrompt = '点击上传按钮';
    }
    return cleanPrompt;
}

/**
 * 处理上传附件操作：AI 点击上传按钮 + 注入本地文件
 *
 * @param {object} agent Midscene PlaywrightAgent
 * @param {import('playwright').Page} page
 * @param {object} step 步骤对象，支持 prompt / filePath / value
 * @returns {Promise<object>} 步骤执行结果
 */
async function handleUpload(agent, page, step) {
    const { prompt } = step;
    const filePath = resolveUploadFilePath(step);
    const pathInPrompt = extractFilePath(prompt);
    const cleanPrompt = buildClickPrompt(prompt || '', pathInPrompt);

    if (!filePath) {
        return {
            success: false,
            error: '无法解析上传文件路径：请在描述中包含文件路径（如"选择文件D:\\testdata\\example.jpg"），或配置 filePath 字段',
            action: 'upload', prompt
        };
    }
    if (!fs.existsSync(filePath)) {
        return { success: false, error: `上传文件不存在: ${filePath}`, action: 'upload', prompt, filePath };
    }

    logger.info(`准备上传附件，文件: ${filePath}`);
    logger.info(`AI点击指令: ${cleanPrompt}`);

    let injected = false;

    // 方式1（优先）：先注册文件选择框监听，AI 点击上传按钮后自动注入文件（模拟真实用户点击上传）
    // 注意：AI 点击失败(replan超限/找不到元素)不能阻断后续兜底注入，必须 try/catch 包裹
    const fileChooserPromise = page.waitForEvent('filechooser', { timeout: FILE_CHOOSER_TIMEOUT_MS }).catch(() => null);
    try {
        await agent.aiAction(cleanPrompt);
    } catch (aiErr) {
        logger.warn(`AI点击上传按钮失败(继续尝试注入): ${aiErr.message}`);
    }
    try {
        const fileChooser = await fileChooserPromise;
        if (fileChooser) {
            await fileChooser.setFiles(filePath);
            injected = true;
            logger.info('已通过文件选择器注入文件（AI点击触发）');
        }
    } catch (e) {
        logger.info('未捕获到文件选择器，尝试直接定位 input[type=file]');
    }

    // 方式0（兜底）：AI 点击未生效时，页面若已存在 input[type=file] 则直接框架级注入
    if (!injected) {
        try {
            const quickInputs = page.locator('input[type=file]');
            if (await quickInputs.count() > 0) {
                await quickInputs.first().setInputFiles(filePath);
                injected = true;
                logger.info('页面存在 input[type=file]，已直接注入文件');
            }
        } catch (e) {
            logger.info(`快速路径注入尝试失败: ${e.message}`);
        }
    }

    // 方式2兜底：直接定位 input[type=file]
    if (!injected) {
        try {
            const input = page.locator('input[type=file]');
            if (await input.count() > 0) {
                await input.first().setInputFiles(filePath);
                injected = true;
                logger.info('已通过 input[type=file] 注入文件');
            }
        } catch (e) {
            logger.info(`定位 input[type=file] 异常: ${e.message}`);
        }
    }

    // 方式3终极兜底：遍历页面所有 input[type=file]，逐个尝试注入（隐藏的 file input 也有效）
    if (!injected) {
        try {
            const inputs = page.locator('input[type=file]');
            const count = await inputs.count();
            for (let i = 0; i < count; i++) {
                try {
                    await inputs.nth(i).setInputFiles(filePath);
                    injected = true;
                    logger.info(`已通过第 ${i + 1} 个 input[type=file] 注入文件`);
                    break;
                } catch (e) { /* 继续尝试下一个 */ }
            }
        } catch (e) {
            logger.info(`遍历注入异常: ${e.message}`);
        }
    }

    if (!injected) {
        return {
            success: false,
            error: '未能注入文件：未触发文件选择框且未找到 input[type=file]',
            action: 'upload', prompt, filePath
        };
    }

    // 等待页面完成上传处理
    await page.waitForTimeout(UPLOAD_SETTLE_MS);
    return { success: true, action: 'upload', prompt, filePath };
}

module.exports = { handleUpload };
