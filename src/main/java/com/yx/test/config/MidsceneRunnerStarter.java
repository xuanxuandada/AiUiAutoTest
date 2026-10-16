package com.yx.test.config;

import com.yx.test.service.TestExecutionService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 应用启动后自动启动 midscene-runner，应用关闭时自动停止 Node.js 进程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MidsceneRunnerStarter implements CommandLineRunner {

    /** 用于在启动时清理「上次运行残留」的执行记录 */
    private final TestExecutionService testExecutionService;

    /** 保存 Node.js 进程引用，用于后续关闭 */
    private Process nodeProcess;

    /** 是否为 Windows 系统 */
    private boolean isWindows;

    @Override
    public void run(String... args) throws Exception {
        log.info("正在启动 midscene-runner...");

        // 第 1 步：先把「上次运行残留」的执行记录清理掉，必须放在启动 Runner 之前。
        //
        // 为什么一定会残留：Runner 是本进程启动的 Node 子进程，应用关闭时 stopRunner()
        // 会把整棵进程树杀掉，Runner 内存里排队/执行中的任务随之消失，
        // 数据库里那些 PENDING/RUNNING 的记录就永远等不到 /api/executions/callback 了。
        // 表现就是界面上有一排任务一直「执行中」，重启多少次都不会变。
        //
        // 这里把它们统一置为 ERROR（而不是删除）：既让界面不再转圈，
        // 又在列表里留下了「这条是重启打断的」痕迹，方便排查。
        // 用 try-catch 包住：清理失败不应该阻止 Runner 启动（比如换个超时/临时锁库）。
        try {
            int fixedCount = testExecutionService.markStaleExecutionsAsError();
            if (fixedCount > 0) {
                log.warn("检测到 {} 条上次运行残留的执行记录（PENDING/RUNNING），已统一标记为 ERROR", fixedCount);
            }
        } catch (Exception e) {
            log.error("清理上次运行残留的执行记录失败（不影响 Runner 启动）", e);
        }

        // midscene-runner 目录路径
        String runnerDir = System.getProperty("user.dir") + File.separator + "midscene-runner";

        // 检查目录是否存在
        File dir = new File(runnerDir);
        if (!dir.exists()) {
            log.warn("midscene-runner 目录不存在: {}，跳过自动启动", runnerDir);
            return;
        }

        // 构建启动命令
        ProcessBuilder processBuilder;
        String os = System.getProperty("os.name").toLowerCase();

        isWindows = os.contains("win");
        if (isWindows) {
            // Windows
            processBuilder = new ProcessBuilder("cmd", "/c", "node", "index.js");
        } else {
            // Linux/Mac
            processBuilder = new ProcessBuilder("node", "index.js");
        }

        processBuilder.directory(dir);
        processBuilder.inheritIO(); // 输出到同一个控制台
        nodeProcess = processBuilder.start(); // 保存进程引用

        log.info("midscene-runner 已启动（端口 3001）");
    }

    /**
     * Spring Boot 关闭时自动停止 Node.js 进程
     * 通过 @PreDestroy 注解，在 JVM 关闭时触发
     */
    @PreDestroy
    public void stopRunner() {
        if (nodeProcess != null && nodeProcess.isAlive()) {
            log.info("正在停止 midscene-runner...");

            if (isWindows) {
                // Windows 上 Process.destroy() 只能杀 cmd 外壳，杀不掉子进程 node
                // 必须用 taskkill /T 杀整个进程树
                try {
                    new ProcessBuilder("taskkill", "/T", "/F", "/PID", String.valueOf(nodeProcess.pid()))
                            .start().waitFor();
                } catch (Exception e) {
                    log.warn("taskkill 执行失败: {}", e.getMessage());
                    nodeProcess.destroyForcibly();
                }
            } else {
                nodeProcess.destroy();
                try {
                    boolean exited = nodeProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (!exited) {
                        nodeProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    nodeProcess.destroyForcibly();
                }
            }

            log.info("midscene-runner 已停止");
        }
    }
}
