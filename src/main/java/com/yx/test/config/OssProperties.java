package com.yx.test.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** OSS Endpoint */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket名称 */
    private String bucketName;

    /** 地域 */
    private String region;
}
