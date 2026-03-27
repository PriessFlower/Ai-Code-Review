package cn.zt.middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCodeReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCodeReviewApplication.class, args);
    }
}
