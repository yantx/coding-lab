package com.galio.jasypt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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

    @Bean("jasyptStringEncryptor")
    public SmartJasyptEncryptor smartJasyptEncryptor() {
        return JasyptContext.getInstance().getStringEncryptor();
    }

//    @Override
//    public int getOrder() {
//        return Ordered.HIGHEST_PRECEDENCE;
//    }
}