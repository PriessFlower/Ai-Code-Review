package cn.zt.middleware.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "wechat")
public class WeChatProperties {
    private String appId;
    private String secret;
    private String templateId;
    private String toUser;
}
