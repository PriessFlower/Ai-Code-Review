package cn.zt.middleware.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {
    private String token;
    private String logRepoUrl;
}
