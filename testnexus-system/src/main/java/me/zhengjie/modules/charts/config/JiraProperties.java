package me.zhengjie.modules.charts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    private String baseUrl;

    private String loginJsp;

    private String filterStats;
}
