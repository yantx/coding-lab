package com.galio.jasypt;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties(JasyptProperties.class)
public class JasyptAutoConfiguration {

    private final JasyptProperties properties;

    public JasyptAutoConfiguration(JasyptProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // 初始化JasyptContext
        JasyptContext.initialize(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StringEncryptor stringEncryptor() {
        return JasyptContext.getInstance().getStringEncryptor();
    }

}