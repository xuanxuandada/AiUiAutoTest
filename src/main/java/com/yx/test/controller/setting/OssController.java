package com.yx.test.controller.setting;

import com.yx.test.common.Result;
import com.yx.test.util.AliyunOssUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * 阿里云OSS文件管理接口 (RESTful)
 */
@RestController
@RequestMapping("/api/oss")
public class OssController {

    @Autowired
    private AliyunOssUtils ossUtils;

    /**
     * 上传文件（支持MultipartFile）
     *
     * @param file      上传的文件
     * @param objectKey OSS对象键（含路径），如 "images/test.png"；不传则使用原文件名
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "objectKey", required = false) String objectKey) {
        // 未指定objectKey时，使用原文件名
        if (objectKey == null || objectKey.isBlank()) {
            objectKey = file.getOriginalFilename();
        }
        String url;
        try {
            url = ossUtils.uploadInputStream(objectKey, file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
        Map<String, Object> data = Map.of(
                "url", url,
                "objectKey", objectKey
        );
        return Result.success(data);
    }

    /**
     * 下载文件（以流的形式返回，浏览器直接下载）
     *
     * @param objectKey OSS对象键（含路径）
     * @return 文件流
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("objectKey") String objectKey) {
        byte[] bytes = ossUtils.downloadBytes(objectKey);
        // 从objectKey中提取文件名
        String fileName = objectKey.contains("/")
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                : objectKey;
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /**
     * 删除文件
     *
     * @param objectKey OSS对象键（含路径）
     * @return 操作结果
     */
    @DeleteMapping
    public Result<Void> delete(@RequestParam("objectKey") String objectKey) {
        ossUtils.deleteFile(objectKey);
        return Result.success();
    }

    /**
     * 检查文件是否存在
     *
     * @param objectKey OSS对象键（含路径）
     * @return 是否存在
     */
    @GetMapping("/exists")
    public Result<Boolean> exists(@RequestParam("objectKey") String objectKey) {
        boolean exists = ossUtils.doesObjectExist(objectKey);
        return Result.success(exists);
    }

    /**
     * 获取文件访问URL
     *
     * @param objectKey OSS对象键（含路径）
     * @return 文件URL
     */
    @GetMapping("/url")
    public Result<String> getUrl(@RequestParam("objectKey") String objectKey) {
        String url = ossUtils.getFileUrl(objectKey);
        return Result.success(url);
    }

    /**
     * 获取临时签名URL（过期后不可访问）
     *
     * @param objectKey    OSS对象键（含路径）
     * @param expireMinutes 过期时间（分钟），默认60分钟
     * @return 临时签名URL
     */
    @GetMapping("/signedUrl")
    public Result<Map<String, Object>> getSignedUrl(@RequestParam("objectKey") String objectKey,
                                            @RequestParam(value = "expireMinutes", defaultValue = "60") Long expireMinutes) {
        Date expiration = Date.from(Instant.now().plusSeconds(expireMinutes * 60));
        String url = ossUtils.getSignedUrl(objectKey, expiration);
        Map<String, Object> data = Map.of(
                "url", url,
                "expireMinutes", expireMinutes
        );
        return Result.success(data);
    }
}
