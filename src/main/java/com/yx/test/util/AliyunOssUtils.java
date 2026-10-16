package com.yx.test.util;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.yx.test.common.BizException;
import com.yx.test.config.OssProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.util.Date;

/**
 * 阿里云OSS工具类
 * 提供文件上传、下载、删除、获取访问链接等功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOssUtils {

    private final OssProperties ossProperties;

    private OSS ossClient;

    /** OSS 是否可用（两个凭据都配了、且客户端建成功才会是 true） */
    private volatile boolean enabled = false;

    @PostConstruct
    public void init() {
        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();

        // 没配 Key 时【不建客户端、只告警】，让应用能正常启动。
        // 为什么这么改：OSS 只是「可选的外部存储」——整个项目里只有 OssController 的上传/下载
        //   几个接口用到它，而用例、套件、执行、截图、报告全都存本地目录，完全不依赖 OSS。
        //   如果这里直接抛异常，@PostConstruct 失败会导致这个 bean 建不出来，
        //   进而把所有依赖它的 bean 一起拖垮，最后整个 Spring 容器起不来——
        //   相当于「一个平时用不到的备用通道」把整个平台卡死了，代价完全不成比例。
        // 注意：这里【绝不回退】到任何硬编码 Key，没配就是没配，需要时用户自己配环境变量。
        if (isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            enabled = false;
            log.warn("未配置阿里云OSS凭据（环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 为空），"
                    + "OSS客户端未初始化，/api/oss/** 相关接口将不可用；"
                    + "平台其余功能（登录、用例、套件、执行、截图、报告）均不受影响。"
                    + "如需使用OSS，请配置上述两个环境变量后重启。");
            return;
        }

        DefaultCredentialProvider credentialsProvider = CredentialsProviderFactory
                .newDefaultCredentialProvider(accessKeyId, accessKeySecret);

        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setSignatureVersion(SignVersion.V4);

        ossClient = OSSClientBuilder.create()
                .endpoint(ossProperties.getEndpoint())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(conf)
                .region(ossProperties.getRegion())
                .build();

        enabled = true;
        log.info("阿里云OSS客户端初始化成功, endpoint={}, bucket={}", ossProperties.getEndpoint(), ossProperties.getBucketName());
    }

    /** 字符串是否为空白（null / 空串 / 纯空格都算） */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * OSS 是否可用。外部（如 OssController）可据此在调用前给出更友好的提示。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 统一入口检查：凡是需要真正访问 OSS 的方法都先调它。
     *
     * 这样「没配 Key」的报错只会发生在用户【主动使用 OSS 的那一刻】，
     * 而不是在应用启动时把整个服务拖垮——错误的影响范围被收窄到「只有 OSS 接口不可用」。
     */
    private void ensureClient() {
        if (ossClient == null) {
            // 这里必须抛 BizException，不能抛普通的 RuntimeException：
            // GlobalExceptionHandler 对 RuntimeException 做了统一兜底，会把 message 改写成
            // 「系统异常，请联系管理员」——那样这句提示就传不到前端了，等于白写；
            // 只有 BizException 有专属分支，message 会原样返回给调用方。
            throw BizException.internalError("阿里云OSS未配置，该功能暂不可用。"
                    + "请配置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 后重启服务");
        }
    }

    // ==================== 上传 ====================

    /**
     * 上传本地文件
     *
     * @param objectKey  OSS对象键（含路径），如 "images/2024/test.png"
     * @param filePath   本地文件完整路径
     * @return 文件访问URL
     */
    public String uploadFile(String objectKey, String filePath) {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            return uploadInputStream(objectKey, inputStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("文件不存在: " + filePath, e);
        } catch (IOException e) {
            throw new RuntimeException("读取本地文件失败: " + filePath, e);
        }
    }

    /**
     * 上传输入流
     *
     * @param objectKey   OSS对象键（含路径）
     * @param inputStream 输入流
     * @return 文件访问URL
     */
    public String uploadInputStream(String objectKey, InputStream inputStream) {
        ensureClient();
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossProperties.getBucketName(), objectKey, inputStream);
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("文件上传成功, objectKey={}, ETag={}", objectKey, result.getETag());
            return getFileUrl(objectKey);
        } catch (OSSException e) {
            log.error("OSS上传失败, ErrorCode={}, ErrorMessage={}, RequestId={}",
                    e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new RuntimeException("文件上传失败: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("OSS客户端异常: {}", e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传字节数组
     *
     * @param objectKey OSS对象键（含路径）
     * @param bytes     文件字节数组
     * @return 文件访问URL
     */
    public String uploadBytes(String objectKey, byte[] bytes) {
        return uploadInputStream(objectKey, new ByteArrayInputStream(bytes));
    }

    // ==================== 下载 ====================

    /**
     * 下载文件到本地指定路径
     *
     * @param objectKey  OSS对象键（含路径）
     * @param targetPath 本地目标文件路径
     */
    public void downloadFile(String objectKey, String targetPath) {
        try (InputStream inputStream = downloadInputStream(objectKey);
             FileOutputStream fos = new FileOutputStream(targetPath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            log.info("文件下载成功, objectKey={}, targetPath={}", objectKey, targetPath);
        } catch (IOException e) {
            log.error("文件下载失败, objectKey={}", objectKey, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件为输入流（调用方需自行关闭流）
     *
     * @param objectKey OSS对象键（含路径）
     * @return 文件输入流
     */
    public InputStream downloadInputStream(String objectKey) {
        ensureClient();
        try {
            OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), objectKey);
            log.info("文件下载流获取成功, objectKey={}", objectKey);
            return ossObject.getObjectContent();
        } catch (OSSException e) {
            log.error("OSS下载失败, ErrorCode={}, ErrorMessage={}", e.getErrorCode(), e.getErrorMessage());
            throw new RuntimeException("文件下载失败: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("OSS客户端异常: {}", e.getMessage());
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件为字节数组
     *
     * @param objectKey OSS对象键（含路径）
     * @return 文件字节数组
     */
    public byte[] downloadBytes(String objectKey) {
        try (InputStream inputStream = downloadInputStream(objectKey)) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取下载流失败: " + e.getMessage(), e);
        }
    }

    // ==================== 删除 ====================

    /**
     * 删除OSS中的文件
     *
     * @param objectKey OSS对象键（含路径）
     */
    public void deleteFile(String objectKey) {
        ensureClient();
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
            log.info("文件删除成功, objectKey={}", objectKey);
        } catch (OSSException e) {
            log.error("OSS删除失败, ErrorCode={}, ErrorMessage={}", e.getErrorCode(), e.getErrorMessage());
            throw new RuntimeException("文件删除失败: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("OSS客户端异常: {}", e.getMessage());
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    // ==================== 其他 ====================

    /**
     * 判断文件是否存在
     *
     * @param objectKey OSS对象键（含路径）
     * @return 是否存在
     */
    public boolean doesObjectExist(String objectKey) {
        // 放在 try 外面：没配 OSS 时希望明确报错，
        // 而不是被下面的 catch 吞掉、静默返回 false（那会让人误以为「文件不存在」）
        ensureClient();
        try {
            return ossClient.doesObjectExist(ossProperties.getBucketName(), objectKey);
        } catch (Exception e) {
            log.error("检查文件是否存在失败, objectKey={}", objectKey, e);
            return false;
        }
    }

    /**
     * 获取文件的公网访问URL（基于Bucket域名）
     *
     * @param objectKey OSS对象键（含路径）
     * @return 文件URL
     */
    public String getFileUrl(String objectKey) {
        // 这个方法本身只拼字符串、不碰网络，理论上不会 NPE。
        // 之所以也加检查：OSS 没配时桶通常也不存在，这里再返回一个「看着正常、点开是404」
        // 的链接，比直接告诉用户「OSS 未配置」更容易让人排查错方向。
        ensureClient();
        // 格式: https://{bucket}.{endpoint-host}/{objectKey}
        String host = ossProperties.getEndpoint().replace("https://", "").replace("http://", "");
        return String.format("https://%s.%s/%s", ossProperties.getBucketName(), host, objectKey);
    }

    /**
     * 生成带签名的临时访问URL（过期后不可访问）
     *
     * @param objectKey      OSS对象键（含路径）
     * @param expirationTime 过期时间
     * @return 带签名的临时URL
     */
    public String getSignedUrl(String objectKey, Date expirationTime) {
        ensureClient();
        try {
            URL url = ossClient.generatePresignedUrl(ossProperties.getBucketName(), objectKey, expirationTime);
            return url.toString();
        } catch (Exception e) {
            log.error("生成签名URL失败, objectKey={}", objectKey, e);
            throw new RuntimeException("生成签名URL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭OSSClient（Spring容器销毁时自动调用）
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("阿里云OSS客户端已关闭");
        }
    }
}
