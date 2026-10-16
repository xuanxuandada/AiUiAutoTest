package com.yx.test.controller.report;

import com.yx.test.entity.TestExecution;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.TestExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.regex.Pattern;

/**
 * 报告 / 截图 文件下载接口（带权限校验）
 *
 * 背景：
 *   以前这两个目录是靠 WebMvcConfig 的 addResourceHandlers 直接映射成本地文件的，
 *   谁都能访问 —— 只要猜到文件名（文件名就是雪花ID），就能下载别人的测试报告。
 *   现在改成由这个 Controller 接管：
 *     1. 先按文件名解析出 executionId
 *     2. 查这条执行记录，拿到它的归属人 userId
 *     3. 校验「这条数据我能不能看」（本人 + 我的所有下级）
 *     4. 通过才把文件流出去
 *
 * 注意文件名做了严格的正则校验（只允许纯数字 + .html / .png），
 * 这样能彻底堵死 ../../ 这种路径穿越攻击。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ArtifactController {

    /** 只允许 /reports/123456.html 这种格式，杜绝路径穿越 */
    private static final Pattern REPORT_NAME = Pattern.compile("^\\d+\\.html$");
    /** 只允许 /screenshots/123456.png 这种格式 */
    private static final Pattern SCREENSHOT_NAME = Pattern.compile("^\\d+\\.png$");

    private final TestExecutionService testExecutionService;
    private final DataScopeService dataScopeService;

    /**
     * 测试报告物理存储目录（与 Runner 的 BACKEND_REPORT_DIR 必须一致，同机共享文件系统）。
     * 默认 G:/aitest/reports；改这里时务必同步改 midscene-runner 的配置，否则两端对不上。
     */
    @Value("${app.artifact.report-dir:G:/aitest/reports}")
    private String reportDir;

    /**
     * 测试截图物理存储目录（与 Runner 的 BACKEND_SCREENSHOT_DIR 必须一致，同机共享文件系统）。
     * 默认 G:/aitest/screenshots。
     */
    @Value("${app.artifact.screenshot-dir:G:/aitest/screenshots}")
    private String screenshotDir;

    /** 测试报告：GET /reports/{fileName} */
    @GetMapping("/reports/{fileName}")
    public ResponseEntity<Resource> report(@PathVariable String fileName) {
        return stream(fileName, new File(reportDir), REPORT_NAME,
                MediaType.valueOf("text/html;charset=UTF-8"));
    }

    /** 测试截图：GET /screenshots/{fileName} */
    @GetMapping("/screenshots/{fileName}")
    public ResponseEntity<Resource> screenshot(@PathVariable String fileName) {
        return stream(fileName, new File(screenshotDir),
                SCREENSHOT_NAME, MediaType.IMAGE_PNG);
    }

    /**
     * 统一的「校验 + 输出文件」逻辑
     *
     * @param fileName    请求的文件名，例如 2082030327007195138.html
     * @param dir         磁盘物理目录（报告/截图均为可配置目录）
     * @param namePattern 文件名白名单正则
     * @param mediaType   返回给浏览器的内容类型
     */
    private ResponseEntity<Resource> stream(String fileName, File dir,
                                            Pattern namePattern, MediaType mediaType) {
        // 1. 文件名白名单校验：不合法直接 404，不透露任何信息
        if (fileName == null || !namePattern.matcher(fileName).matches()) {
            return ResponseEntity.notFound().build();
        }

        // 2. 文件名去掉后缀就是 executionId（这两类文件是按 executionId 命名的）
        Long executionId;
        try {
            executionId = Long.parseLong(fileName.substring(0, fileName.lastIndexOf('.')));
        } catch (NumberFormatException e) {
            return ResponseEntity.notFound().build();
        }

        // 3. 查执行记录：文件必须对应一条真实记录，否则不给下
        TestExecution execution = testExecutionService.getById(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        // 4. 数据隔离：不是我的、也不是我下级创建的 → 抛 403（由全局异常处理器转成 JSON）
        dataScopeService.assertCanAccess(execution.getUserId());

        // 5. 定位磁盘文件
        File file = new File(dir, fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        // 6. 用 Resource 流式输出（报告几十MB也不会一次性读到内存里）
        //    加 noStore：这是私有数据，不允许浏览器/CDN 缓存
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(file));
    }
}
